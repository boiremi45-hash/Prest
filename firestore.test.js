const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_vfx_123";
const BOB_UID = "bob_vfx_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastTimestamp() {
  return new Date(Date.now() - 1000);
}

function validPresetPayload(uid) {
  const ts = pastTimestamp();
  return {
    userId: uid,
    name: "Teal & Orange Blockbuster",
    fileName: "teal_orange_blockbuster.ffx",
    category: "Color Grade",
    description: "Warm skin tones with deep cyan shadows",
    sourceType: "studio",
    ffxVersion: "AE 2024 (RIFX)",
    effectModules: ["ADBE Lumetri", "ADBE Vignette"],
    tags: ["cinema", "lut"],
    isFavorite: false,
    exposure: 0.1,
    contrast: 0.25,
    saturation: 12.0,
    hueShift: 0.0,
    temperature: 0.15,
    tint: -0.05,
    vignetteIntensity: 0.35,
    glowIntensity: 0.15,
    grainAmount: 0.08,
    zoomPulse: 0.0,
    shakeIntensity: 0.0,
    rotationDegrees: 0.0,
    speedMultiplier: 1.0,
    letterboxRatio: 0.12,
    rawHeaderSnippet: "RIFX..FFX..ADBE Lumetri",
    usageCount: 0,
    createdAt: ts,
    updatedAt: ts,
  };
}

function validExportPayload(uid) {
  return {
    userId: uid,
    presetId: "preset_1",
    presetName: "Teal & Orange Blockbuster",
    outputFileName: "PresetFX_clip_01.mp4",
    resolutionLabel: "1080p Full HD",
    codecLabel: "H.264 / AVC",
    bitrateMbps: 24,
    fileSizeBytes: 12456000,
    durationMs: 8500,
    localUriString: "content://media/external/downloads/1001",
    createdAt: pastTimestamp(),
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: cannot list or create presets", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("presets").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("presets").doc("p1").set(validPresetPayload(ALICE_UID))
  );
});

test("Authenticated owner: can create, read, update, and delete own preset", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ref = aliceDb.collection("users").doc(ALICE_UID).collection("presets").doc("preset_1");

  await assertSucceeds(ref.set(validPresetPayload(ALICE_UID)));
  await assertSucceeds(ref.get());
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("presets").get());
  await assertSucceeds(
    ref.update({
      isFavorite: true,
      contrast: 0.4,
      updatedAt: pastTimestamp(),
    })
  );
  await assertSucceeds(ref.delete());
});

test("Cross-user isolation: Bob cannot read, list, create, update, or delete Alice's presets", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("presets").doc("preset_1").set(validPresetPayload(ALICE_UID))
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("presets").doc("preset_1").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("presets").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("presets").doc("preset_2").set(validPresetPayload(BOB_UID))
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("presets").doc("preset_1").update({
      name: "Hacked",
      updatedAt: pastTimestamp(),
    })
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("presets").doc("preset_1").delete());
});

test("Schema validation: rejects ghost fields, invalid bounds, and immutable userId/createdAt overwrite", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ref = aliceDb.collection("users").doc(ALICE_UID).collection("presets").doc("preset_bad");

  const withGhost = { ...validPresetPayload(ALICE_UID), isAdmin: true };
  await assertFails(ref.set(withGhost));

  const outOfBounds = { ...validPresetPayload(ALICE_UID), exposure: 5.0 };
  await assertFails(ref.set(outOfBounds));

  const invalidCategory = { ...validPresetPayload(ALICE_UID), category: "Unknown" };
  await assertFails(ref.set(invalidCategory));

  await assertSucceeds(ref.set(validPresetPayload(ALICE_UID)));
  await assertFails(
    ref.update({
      userId: BOB_UID,
      updatedAt: pastTimestamp(),
    })
  );
});

test("Export records: owner can create and read, update is forbidden, cross-user fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ref = aliceDb.collection("users").doc(ALICE_UID).collection("exports").doc("exp_1");

  await assertSucceeds(ref.set(validExportPayload(ALICE_UID)));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("exports").get());
  await assertFails(ref.update({ bitrateMbps: 50 }));

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("exports").get());

  await assertSucceeds(ref.delete());
});

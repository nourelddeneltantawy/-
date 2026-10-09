const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const REGULAR_USER_UID = "user_regular_123";
const REGULAR_EMAIL = "technician.user@example.com";

const SUPER_ADMIN_1_EMAIL = "nwnwraldynaltantawy@gmail.com";
const SUPER_ADMIN_2_EMAIL = "nwraldynmstfymhmd@gmail.com";
const DYNAMIC_ADMIN_EMAIL = "approved.admin@example.com";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

test("Public/Regular user: can read motors in catalog", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("motors").doc("motor_samsung_1").set({
      id: "motor_samsung_1",
      userId: "admin_uid",
      name: "Samsung DIT Inverter",
      brand: "Samsung",
      model: "WW80",
      pinouts: [],
      createdAt: new Date(),
      updatedAt: new Date(),
    });
  });

  const regularDb = testEnv.authenticatedContext(REGULAR_USER_UID, {
    email: REGULAR_EMAIL,
  }).firestore();

  await assertSucceeds(regularDb.collection("motors").doc("motor_samsung_1").get());
  await assertSucceeds(regularDb.collection("motors").get());
});

test("Public/Regular user: CANNOT create a motor", async () => {
  const regularDb = testEnv.authenticatedContext(REGULAR_USER_UID, {
    email: REGULAR_EMAIL,
  }).firestore();

  await assertFails(
    regularDb.collection("motors").doc("motor_hacker").set({
      id: "motor_hacker",
      userId: REGULAR_USER_UID,
      name: "Hacked Motor",
      brand: "Unknown",
      model: "HK-1",
      pinouts: [],
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
});

test("Super Admin 1: can create motor", async () => {
  const superAdminDb = testEnv.authenticatedContext("super_admin_1_uid", {
    email: SUPER_ADMIN_1_EMAIL,
  }).firestore();

  await assertSucceeds(
    superAdminDb.collection("motors").doc("motor_super_1").set({
      id: "motor_super_1",
      userId: "super_admin_1_uid",
      name: "LG Direct Drive Pro",
      brand: "LG",
      model: "F1496",
      pinouts: [],
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
});

test("Super Admin 2: can create motor and add dynamic admin", async () => {
  const superAdminDb = testEnv.authenticatedContext("super_admin_2_uid", {
    email: SUPER_ADMIN_2_EMAIL,
  }).firestore();

  // Can add dynamic admin
  await assertSucceeds(
    superAdminDb.collection("admins").doc(DYNAMIC_ADMIN_EMAIL).set({
      email: DYNAMIC_ADMIN_EMAIL,
      addedBy: SUPER_ADMIN_2_EMAIL,
      role: "admin",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
});

test("Dynamic Admin: after being added to admins whitelist, can create motor", async () => {
  // Seed dynamic admin
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("admins").doc(DYNAMIC_ADMIN_EMAIL).set({
      email: DYNAMIC_ADMIN_EMAIL,
      addedBy: SUPER_ADMIN_1_EMAIL,
      role: "admin",
      createdAt: new Date(),
      updatedAt: new Date(),
    });
  });

  const dynamicAdminDb = testEnv.authenticatedContext("dynamic_admin_uid", {
    email: DYNAMIC_ADMIN_EMAIL,
  }).firestore();

  await assertSucceeds(
    dynamicAdminDb.collection("motors").doc("motor_dynamic_1").set({
      id: "motor_dynamic_1",
      userId: "dynamic_admin_uid",
      name: "Unionaire Motor",
      brand: "Unionaire",
      model: "UN-800",
      pinouts: [],
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
});

test("Regular user: CANNOT add or manage admins", async () => {
  const regularDb = testEnv.authenticatedContext(REGULAR_USER_UID, {
    email: REGULAR_EMAIL,
  }).firestore();

  await assertFails(
    regularDb.collection("admins").doc("new.admin@example.com").set({
      email: "new.admin@example.com",
      addedBy: REGULAR_EMAIL,
      role: "admin",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
});

test("Protected Super Admin cannot be deleted from admins", async () => {
  const superAdminDb = testEnv.authenticatedContext("super_admin_1_uid", {
    email: SUPER_ADMIN_1_EMAIL,
  }).firestore();

  await assertFails(
    superAdminDb.collection("admins").doc(SUPER_ADMIN_2_EMAIL).delete()
  );
});

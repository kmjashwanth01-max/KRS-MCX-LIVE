const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_UID = "admin_jashwanth_01";
const ADMIN_EMAIL = "kmjashwanth01@gmail.com";
const ALICE_UID = "alice_trader";
const BOB_UID = "bob_trader";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
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

test("App client can create and get individual user_account and user_files (.txt) by username, but CANNOT list all user_accounts or user_files", async () => {
  const clientDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date();
  const sampleInfoLine =
    "USERNAME: @alice_trader | FULL NAME: Alice Trader | PIN: 4321 | CITY: Coimbatore | ROLE: Bullion Trader | EMAIL: alice@test.com | PHONE: +91 98422 11111 | THEME: Light Platinum Mode";
  const sampleTextFile =
    "FILE_NAME: alice_trader.txt\nUSERNAME: alice_trader\nPASSWORD_PIN: 4321\nCITY: Coimbatore\nROLE: Bullion Trader";

  await assertSucceeds(
    clientDb.collection("user_accounts").doc(ALICE_UID).set({
      userId: ALICE_UID,
      fileName: "alice_trader.txt",
      userTextFile: sampleTextFile,
      username: "alice_trader",
      name: "Alice Trader",
      email: "alice@test.com",
      phone: "+91 98422 11111",
      city: "Coimbatore",
      role: "Bullion Trader",
      pin4Digit: "4321",
      isDarkMode: false,
      isAppLockEnabled: true,
      isBiometricEnabled: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    clientDb.collection("user_files").doc(ALICE_UID).set({
      fileName: "alice_trader.txt",
      userInfoText: sampleInfoLine,
      fileContentText: sampleTextFile,
      username: "alice_trader",
      name: "Alice Trader",
      pin4Digit: "4321",
      city: "Coimbatore",
      role: "Bullion Trader",
      email: "alice@test.com",
      phone: "+91 98422 11111",
      themeMode: "Light Platinum Mode",
      createdAt: now,
      updatedAt: now,
    })
  );

  // App can look up individual account & text file by username to verify existence and PIN on Sign In
  await assertSucceeds(clientDb.collection("user_accounts").doc(ALICE_UID).get());
  await assertSucceeds(clientDb.collection("user_files").doc(ALICE_UID).get());

  // Regular user / unauthenticated client CANNOT list all accounts or all user files
  await assertFails(clientDb.collection("user_accounts").get());
  await assertFails(clientDb.collection("user_files").get());
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("user_accounts").get());
  await assertFails(bobDb.collection("user_files").get());
});

test("App client can log login_events and admin_mail_logs, but CANNOT read or list them", async () => {
  const clientDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date();

  await assertSucceeds(
    clientDb.collection("login_events").doc("evt_alice_1").set({
      userId: ALICE_UID,
      username: "alice_trader",
      name: "Alice Trader",
      eventType: "USER_SIGN_IN",
      savedTheme: "Light Platinum Mode",
      totalAccountsCount: 2,
      createdAt: now,
    })
  );

  await assertSucceeds(
    clientDb.collection("admin_mail_logs").doc("mail_alice_1").set({
      userId: ALICE_UID,
      recipientEmail: "krsmcxofficial@gmail.com",
      subject: "[KRS Bullion] USER_SIGN_IN",
      body: "Total Accounts Created: 2",
      eventType: "USER_SIGN_IN",
      totalAccountsCount: 2,
      createdAt: now,
    })
  );

  // Regular users are strictly forbidden from viewing or listing login_events or admin_mail_logs
  await assertFails(clientDb.collection("login_events").doc("evt_alice_1").get());
  await assertFails(clientDb.collection("login_events").get());
  await assertFails(clientDb.collection("admin_mail_logs").doc("mail_alice_1").get());
  await assertFails(clientDb.collection("admin_mail_logs").get());
});

test("ONLY Admin (verified kmjashwanth01@gmail.com or /admins/{uid}) can list user_accounts, user_files, login_events, admin_mail_logs, and publish announcements", async () => {
  const now = new Date();

  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("admins").doc(ADMIN_UID).set({ role: "admin" });
    await db.collection("login_events").doc("evt_1").set({
      userId: ALICE_UID,
      username: "alice",
      name: "Alice",
      eventType: "ACCOUNT_CREATED",
      savedTheme: "Dark",
      totalAccountsCount: 1,
      createdAt: now,
    });
    await db.collection("admin_mail_logs").doc("mail_1").set({
      userId: ALICE_UID,
      recipientEmail: "krsmcxofficial@gmail.com",
      subject: "Subject",
      body: "Body",
      eventType: "ACCOUNT_CREATED",
      totalAccountsCount: 1,
      createdAt: now,
    });
  });

  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, {
      email: ADMIN_EMAIL,
      email_verified: true,
    })
    .firestore();

  // Admin can list all login_events, admin_mail_logs, user_accounts, and user_files
  await assertSucceeds(adminDb.collection("login_events").get());
  await assertSucceeds(adminDb.collection("admin_mail_logs").get());
  await assertSucceeds(adminDb.collection("user_accounts").get());
  await assertSucceeds(adminDb.collection("user_files").get());

  // Admin can publish an official announcement
  await assertSucceeds(
    adminDb.collection("announcements").doc("ann_1").set({
      authorId: ADMIN_UID,
      title: "MCX Evening Session Alert",
      message: "Live Bullion 1-second feed active with special festival bullion rates.",
      priority: "high",
      isActive: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Regular user can read active announcements, but CANNOT create arbitrary announcements
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("announcements").where("isActive", "==", true).get()
  );
  await assertFails(
    aliceDb.collection("announcements").doc("ann_fake").set({
      authorId: ALICE_UID,
      title: "Fake Announcement",
      message: "Unauthorized",
      priority: "normal",
      isActive: true,
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Email spoofing test: unverified admin email is rejected", async () => {
  const spoofedAdminDb = testEnv
    .authenticatedContext("spoof_uid", {
      email: ADMIN_EMAIL,
      email_verified: false,
    })
    .firestore();

  await assertFails(spoofedAdminDb.collection("login_events").get());
  await assertFails(spoofedAdminDb.collection("admin_mail_logs").get());
  await assertFails(spoofedAdminDb.collection("user_accounts").get());
  await assertFails(spoofedAdminDb.collection("user_files").get());
});

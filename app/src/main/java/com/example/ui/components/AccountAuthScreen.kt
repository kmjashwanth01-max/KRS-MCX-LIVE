package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.*
import com.example.util.BiometricAuthManager
import com.example.util.UserAccountManager
import com.example.util.UserProfile
import kotlinx.coroutines.launch

/**
 * Dedicated Onboarding & Authentication Screen.
 * - Checks whether an account already exists in the Database Manager on Create Account,
 *   and if it exists, shows a Popup Dialog ("Account Already Exists — Sign In") that routes to Sign In.
 * - Verifies Username and Password / 4-Digit PIN against the App Database on Sign In,
 *   granting access on match and denying access with a Popup Dialog on mismatch.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountAuthScreen(
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? androidx.fragment.app.FragmentActivity

    val savedUser by UserAccountManager.currentUser.collectAsState()
    val allSavedAccounts by UserAccountManager.allAccounts.collectAsState()

    var isSignInTab by remember { mutableStateOf(savedUser != null || allSavedAccounts.isNotEmpty()) }

    // Registration States
    var regUsername by remember { mutableStateOf("") }
    var regName by remember { mutableStateOf("") }
    var regEmail by remember { mutableStateOf("") }
    var regPhone by remember { mutableStateOf("") }
    var regCity by remember { mutableStateOf("Coimbatore") }
    var regSelectedRole by remember { mutableStateOf("Bullion Trader") }
    var regPin by remember { mutableStateOf("") }
    var regConfirmPin by remember { mutableStateOf("") }
    var regShowPin by remember { mutableStateOf(false) }
    var regEnableBiometric by remember { mutableStateOf(true) }
    var regValidationError by remember { mutableStateOf<String?>(null) }

    // Popup Dialog State when trying to create an account that already exists in the Database Manager
    var existingAccountForPopup by remember { mutableStateOf<UserProfile?>(null) }

    // Sign In States
    var signInUsername by remember {
        mutableStateOf(savedUser?.username ?: allSavedAccounts.lastOrNull()?.username ?: "")
    }
    var signInPin by remember { mutableStateOf("") }
    var signInShowPin by remember { mutableStateOf(false) }
    var signInError by remember { mutableStateOf<String?>(null) }
    var accessDeniedPopupMessage by remember { mutableStateOf<String?>(null) }
    var accessDeniedAccountMissing by remember { mutableStateOf(false) }

    val roles = listOf(
        "Bullion Trader",
        "Jewelry Retailer",
        "Bullion Refinery",
        "Gold/Silver Investor"
    )

    val isBiometricReady = remember { BiometricAuthManager.isBiometricAvailable(context) }
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember(context) { androidx.credentials.CredentialManager.create(context) }

    LaunchedEffect(Unit) {
        UserAccountManager.syncAllAccountsToCloudNow()
        if (!UserAccountManager.isExplicitlyLoggedOut()) {
            com.example.util.GoogleAuthManager.attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = { firebaseUser ->
                    UserAccountManager.onGoogleSignIn(
                        uid = firebaseUser.uid,
                        email = firebaseUser.email,
                        displayName = firebaseUser.displayName
                    )
                    onAuthSuccess()
                },
                onUnauthenticated = {},
                scope = coroutineScope
            )
        }
    }

    // POPUP 1: "ACCOUNT ALREADY EXISTS - SIGN IN" DIALOG
    val existingAcc = existingAccountForPopup
    if (existingAcc != null) {
        AlertDialog(
            onDismissRequest = { existingAccountForPopup = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = "Account Exists",
                    tint = GoldPrimary,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Account Already Exists",
                    color = GoldPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "The account '@${existingAcc.username}' (${existingAcc.name}) already exists in our database manager.",
                        color = Color.White,
                        fontSize = 13.5.sp,
                        lineHeight = 19.sp
                    )
                    Text(
                        text = "Please sign in with your existing Username and Password / PIN to access your account.",
                        color = GoldLight,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val targetUser = existingAcc.username
                        existingAccountForPopup = null
                        regValidationError = null
                        signInError = null
                        signInUsername = targetUser
                        signInPin = ""
                        isSignInTab = true
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GoldPrimary,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier.testTag("popup_account_exists_sign_in_btn")
                ) {
                    Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sign In Now", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { existingAccountForPopup = null },
                    border = androidx.compose.foundation.BorderStroke(1.dp, TitaniumBorder)
                ) {
                    Text("Cancel", color = Color.LightGray)
                }
            },
            containerColor = TitaniumDarkSurface,
            modifier = Modifier.testTag("account_exists_popup")
        )
    }

    // POPUP 2: "ACCESS DENIED" VERIFICATION DIALOG ON SIGN IN
    val deniedMsg = accessDeniedPopupMessage
    if (deniedMsg != null) {
        AlertDialog(
            onDismissRequest = { accessDeniedPopupMessage = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = "Access Denied",
                    tint = Color(0xFFFF5252),
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Access Denied",
                    color = Color(0xFFFF5252),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = deniedMsg,
                    color = Color.White,
                    fontSize = 13.5.sp,
                    lineHeight = 19.sp
                )
            },
            confirmButton = {
                if (accessDeniedAccountMissing) {
                    Button(
                        onClick = {
                            val attemptedUser = signInUsername.trim().removePrefix("@")
                            accessDeniedPopupMessage = null
                            signInError = null
                            regUsername = attemptedUser
                            isSignInTab = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GoldPrimary,
                            contentColor = Color.Black
                        )
                    ) {
                        Text("Create Account", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = {
                            accessDeniedPopupMessage = null
                            signInPin = ""
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GoldPrimary,
                            contentColor = Color.Black
                        )
                    ) {
                        Text("Try Again", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                if (accessDeniedAccountMissing) {
                    OutlinedButton(
                        onClick = { accessDeniedPopupMessage = null },
                        border = androidx.compose.foundation.BorderStroke(1.dp, TitaniumBorder)
                    ) {
                        Text("Close", color = Color.LightGray)
                    }
                }
            },
            containerColor = TitaniumDarkSurface,
            modifier = Modifier.testTag("access_denied_popup")
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("account_auth_screen"),
        color = TitaniumBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // 1. BRAND HEADER: K.R.S. Gold & Silver Logo & JASHX AI Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_krs_app_logo),
                    contentDescription = "K.R.S. Gold & Silver Logo",
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.5.dp, GoldPrimary, RoundedCornerShape(12.dp))
                )
                Spacer(modifier = Modifier.width(14.dp))
                Image(
                    painter = painterResource(id = R.drawable.ic_jashx_ai_logo),
                    contentDescription = "JASHX AI Logo",
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.5.dp, GoldPrimary, RoundedCornerShape(12.dp))
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "KRS GOLD & SILVER",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = GoldPrimary,
                fontSize = 22.sp,
                letterSpacing = 1.sp
            )
            Text(
                text = "Live 1s MCX Rates • KRS Live Stream",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.LightGray,
                fontSize = 12.sp
            )

            // "Powered By JASHX AI" Badge
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = GoldPrimary.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_jashx_ai_logo),
                        contentDescription = "JASHX AI Logo",
                        modifier = Modifier.size(16.dp).clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = " Powered By JASHX AI ",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = GoldPrimary,
                        fontSize = 11.sp
                    )
                }
            }

            // Database Verification Security Notice
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = TitaniumDarkSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MarketGreen.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = "Verified Account Security",
                        tint = MarketGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Verified Account & Database Security",
                            color = MarketGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Checks existing accounts before creation and verifies your Username & Password / PIN with the app database on sign-in.",
                            color = Color.LightGray,
                            fontSize = 10.5.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 2. TAB SWITCHER: Create Account vs Sign In
            TabRow(
                selectedTabIndex = if (isSignInTab) 1 else 0,
                containerColor = TitaniumDarkSurface,
                contentColor = GoldPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, TitaniumBorder, RoundedCornerShape(12.dp))
            ) {
                Tab(
                    selected = !isSignInTab,
                    onClick = {
                        isSignInTab = false
                        regValidationError = null
                    },
                    text = {
                        Text(
                            text = "Create Account",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (!isSignInTab) GoldPrimary else Color.LightGray
                        )
                    },
                    modifier = Modifier.testTag("tab_create_account")
                )
                Tab(
                    selected = isSignInTab,
                    onClick = {
                        isSignInTab = true
                        signInError = null
                        if (signInUsername.isBlank()) {
                            signInUsername = savedUser?.username ?: allSavedAccounts.lastOrNull()?.username ?: ""
                        }
                    },
                    text = {
                        Text(
                            text = "Sign In",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (isSignInTab) GoldPrimary else Color.LightGray
                        )
                    },
                    modifier = Modifier.testTag("tab_sign_in")
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. TAB CONTENT
            if (!isSignInTab) {
                // ==================== CREATE ACCOUNT FLOW ====================
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Create Personal Bullion Account",
                        color = GoldLight,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // 1. Username Field
                    OutlinedTextField(
                        value = regUsername,
                        onValueChange = { regUsername = it; regValidationError = null },
                        label = { Text("Username *") },
                        placeholder = { Text("e.g. jashwanth01") },
                        leadingIcon = { Icon(Icons.Default.AlternateEmail, contentDescription = null, tint = GoldPrimary) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = TitaniumBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("auth_input_username")
                    )

                    // 2. Full Name Field
                    OutlinedTextField(
                        value = regName,
                        onValueChange = {
                            regName = it
                            regValidationError = null
                            if (regUsername.isBlank()) {
                                regUsername = it.trim().lowercase().replace("\\s+".toRegex(), "_")
                            }
                        },
                        label = { Text("Full Name *") },
                        placeholder = { Text("e.g. K.M. Jashwanth") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = GoldPrimary) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = TitaniumBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("auth_input_name")
                    )

                    // 3. Mobile Number
                    OutlinedTextField(
                        value = regPhone,
                        onValueChange = { regPhone = it; regValidationError = null },
                        label = { Text("Mobile Number (for WhatsApp Alerts) *") },
                        placeholder = { Text("+91 98422 12345") },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = GoldPrimary) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = TitaniumBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("auth_input_phone")
                    )


                    // 5. Email Address
                    OutlinedTextField(
                        value = regEmail,
                        onValueChange = { regEmail = it; regValidationError = null },
                        label = { Text("Email Address (Optional)") },
                        placeholder = { Text("trader@example.com") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = GoldPrimary) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = TitaniumBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("auth_input_email")
                    )

                    // 6. City / Bullion Market
                    OutlinedTextField(
                        value = regCity,
                        onValueChange = { regCity = it; regValidationError = null },
                        label = { Text("City / Bullion Market") },
                        placeholder = { Text("Coimbatore") },
                        leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = GoldPrimary) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = TitaniumBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("auth_input_city")
                    )

                    // 7. Trading Role Chips
                    Text(
                        text = "Business / Trading Role:",
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        roles.take(2).forEach { role ->
                            FilterChip(
                                selected = regSelectedRole == role,
                                onClick = { regSelectedRole = role },
                                label = { Text(role, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = GoldPrimary,
                                    selectedLabelColor = Color.Black
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        roles.drop(2).forEach { role ->
                            FilterChip(
                                selected = regSelectedRole == role,
                                onClick = { regSelectedRole = role },
                                label = { Text(role, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = GoldPrimary,
                                    selectedLabelColor = Color.Black
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Validation Error Text
                    if (regValidationError != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF3E1212),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF5252)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = regValidationError ?: "",
                                color = Color(0xFFFF8A80),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Primary Button: Check Database Manager & Create Account
                    Button(
                        onClick = {
                            val cleanUser = regUsername.trim().removePrefix("@")
                            val cleanName = regName.trim()
                            val cleanPhone = regPhone.trim()

                            if (cleanUser.isBlank()) {
                                regValidationError = "Please enter a username for your account."
                                return@Button
                            }
                            if (cleanName.isBlank()) {
                                regValidationError = "Please enter your full name."
                                return@Button
                            }
                            if (cleanPhone.isBlank()) {
                                regValidationError = "Please enter your mobile number."
                                return@Button
                            }

                            coroutineScope.launch {
                                // Sync account from Cloud Firestore first if present
                                UserAccountManager.fetchAndSyncAccountFromCloudIfPresent(cleanUser)

                                // 1. Check whether the account already exists in our Database Manager
                                val existingAccount = UserAccountManager.findExistingAccount(
                                    username = cleanUser,
                                    email = regEmail,
                                    phone = cleanPhone
                                )
                                if (existingAccount != null) {
                                    existingAccountForPopup = existingAccount
                                    regValidationError = "Account '@${existingAccount.username}' already exists! Please sign in."
                                    return@launch
                                }

                                // 2. Account is brand-new -> Create & Save in Database Manager
                                val created = UserAccountManager.createAccount(
                                    username = cleanUser,
                                    name = cleanName,
                                    email = regEmail,
                                    phone = cleanPhone,
                                    city = regCity,
                                    role = regSelectedRole,
                                    pin4Digit = "1234",
                                    enableLockImmediately = false,
                                    enableBiometric = false
                                )
                                UserAccountManager.completeOnboarding()
                                UserAccountManager.unlockApp()
                                Toast.makeText(
                                    context,
                                    "Account Created! Welcome @${created.username}!",
                                    Toast.LENGTH_LONG
                                ).show()
                                onAuthSuccess()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = Color.Black),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("auth_submit_button")
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Create Account & Enter Market",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    com.example.util.GoogleSignInButton(
                        onAuthSuccess = { firebaseUser ->
                            UserAccountManager.onGoogleSignIn(
                                uid = firebaseUser.uid,
                                email = firebaseUser.email,
                                displayName = firebaseUser.displayName
                            )
                            Toast.makeText(
                                context,
                                "Welcome, ${firebaseUser.displayName ?: firebaseUser.email ?: "Trader"}!",
                                Toast.LENGTH_SHORT
                            ).show()
                            onAuthSuccess()
                        },
                        onAuthError = { err ->
                            regValidationError = err
                        }
                    )

                    // Bottom switcher link
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                isSignInTab = true
                                signInError = null
                            }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Already have an account? ",
                            color = Color.LightGray,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "Sign In with Username & Password / PIN",
                            color = GoldPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                // ==================== SIGN IN FLOW (USERNAME & PASSWORD / PIN) ====================
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Sign In to KRS Bullion Terminal",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Enter your Username to sign in to the bullion terminal",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )

                    // Saved Account Hint Banner if an account is already registered in the database on this phone
                    val displayAccount = savedUser ?: allSavedAccounts.lastOrNull()
                    if (displayAccount != null) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = TitaniumDarkSurface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    signInUsername = displayAccount.username
                                    signInError = null
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(GoldPrimary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = displayAccount.initials,
                                        color = Color.Black,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Registered Account in App Database:",
                                        color = GoldLight,
                                        fontSize = 10.5.sp
                                    )
                                    Text(
                                        text = "@${displayAccount.username} (${displayAccount.name})",
                                        color = Color.White,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "Auto-fill",
                                    color = MarketGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // 1. Sign In Username Field
                    OutlinedTextField(
                        value = signInUsername,
                        onValueChange = {
                            signInUsername = it
                            signInError = null
                        },
                        label = { Text("Username *") },
                        placeholder = { Text("e.g. jashwanth01") },
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null, tint = GoldPrimary)
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = TitaniumBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sign_in_username_input")
                    )

                    // Error Message Banner
                    if (signInError != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF3E1212),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF5252)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = signInError ?: "",
                                color = Color(0xFFFF8A80),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }

                    // Direct Submit Button: Verify Username with App Database
                    Button(
                        onClick = {
                            val cleanUser = signInUsername.trim()

                            if (cleanUser.isBlank()) {
                                signInError = "Please enter your username."
                                return@Button
                            }

                            coroutineScope.launch {
                                // Sync account from Cloud Firestore first if present
                                UserAccountManager.fetchAndSyncAccountFromCloudIfPresent(cleanUser)

                                val (success, err) = UserAccountManager.signIn(cleanUser)
                                if (success) {
                                    Toast.makeText(
                                        context,
                                        "Welcome back, @${UserAccountManager.currentUser.value?.username ?: cleanUser}!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    onAuthSuccess()
                                } else {
                                    val message = err ?: "Account '@$cleanUser' does not exist."
                                    signInError = message
                                    accessDeniedAccountMissing = message.contains("does not exist", ignoreCase = true)
                                    accessDeniedPopupMessage = message
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = Color.Black),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("sign_in_submit_button")
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sign In to Market", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    com.example.util.GoogleSignInButton(
                        onAuthSuccess = { firebaseUser ->
                            UserAccountManager.onGoogleSignIn(
                                uid = firebaseUser.uid,
                                email = firebaseUser.email,
                                displayName = firebaseUser.displayName
                            )
                            Toast.makeText(
                                context,
                                "Welcome back, ${firebaseUser.displayName ?: firebaseUser.email ?: "Trader"}!",
                                Toast.LENGTH_SHORT
                            ).show()
                            onAuthSuccess()
                        },
                        onAuthError = { err ->
                            signInError = err
                        }
                    )

                    // Switch to Create Account
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                isSignInTab = false
                                regValidationError = null
                            }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "New user or new phone? ",
                            color = Color.LightGray,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "Create Account",
                            color = GoldPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Footer Attribution
            Text(
                text = "This app was build by K.M. Jashwanth",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                fontSize = 11.sp
            )
            Text(
                text = "Official Inquiries: krsmcxofficial@gmail.com",
                style = MaterialTheme.typography.labelSmall,
                color = GoldLight,
                textAlign = TextAlign.Center,
                fontSize = 10.sp
            )
        }
    }
}

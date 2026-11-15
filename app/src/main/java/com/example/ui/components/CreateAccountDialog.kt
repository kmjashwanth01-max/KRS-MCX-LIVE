package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.*
import com.example.util.UserAccountManager
import com.example.util.UserProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateAccountDialog(
    onDismiss: () -> Unit,
    onAccountCreated: (UserProfile) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf("Bullion Trader") }
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var enableLockNow by remember { mutableStateOf(true) }
    var enableBiometricNow by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }
    var existingAccountPopup by remember { mutableStateOf<UserProfile?>(null) }

    val roles = listOf(
        "Bullion Trader",
        "Jewelry Retailer",
        "Gold/Silver Investor",
        "Individual Buyer"
    )

    val foundExisting = existingAccountPopup
    if (foundExisting != null) {
        AlertDialog(
            onDismissRequest = { existingAccountPopup = null },
            title = {
                Text(
                    text = "Account Already Exists",
                    color = GoldPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp
                )
            },
            text = {
                Text(
                    text = "The account '@${foundExisting.username}' (${foundExisting.name}) already exists in our database manager.\n\nPlease sign in with your Username and Password / PIN.",
                    color = Color.White,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        existingAccountPopup = null
                        onDismiss()
                        UserAccountManager.signOut()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = Color.Black)
                ) {
                    Text("Sign In Now", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { existingAccountPopup = null }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = TitaniumDarkSurface
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = TitaniumDarkSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 20.dp)
                .testTag("create_account_dialog")
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = "Create Account",
                            tint = GoldPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Create Individual Account",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextWhitePrimary
                            )
                            Text(
                                text = "KRS Bullion Personal Profile & PIN Lock",
                                style = MaterialTheme.typography.labelSmall,
                                color = GoldLight
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Username
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it; validationError = null },
                    label = { Text("Username *", fontSize = 12.sp) },
                    placeholder = { Text("e.g. arun_trader", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.AlternateEmail, contentDescription = null, tint = GoldPrimary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhitePrimary,
                        unfocusedTextColor = TextWhitePrimary,
                        focusedBorderColor = GoldPrimary,
                        unfocusedBorderColor = TitaniumBorder
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("input_account_username"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Full Name
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        validationError = null
                        if (username.isBlank()) {
                            username = it.trim().lowercase().replace("\\s+".toRegex(), "_")
                        }
                    },
                    label = { Text("Full Name *", fontSize = 12.sp) },
                    placeholder = { Text("e.g. Arun Kumar", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = GoldPrimary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhitePrimary,
                        unfocusedTextColor = TextWhitePrimary,
                        focusedBorderColor = GoldPrimary,
                        unfocusedBorderColor = TitaniumBorder
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("input_account_name"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Email
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; validationError = null },
                    label = { Text("Email Address *", fontSize = 12.sp) },
                    placeholder = { Text("e.g. arun@example.com", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = GoldPrimary) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhitePrimary,
                        unfocusedTextColor = TextWhitePrimary,
                        focusedBorderColor = GoldPrimary,
                        unfocusedBorderColor = TitaniumBorder
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("input_account_email"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Phone & City Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Phone Number", fontSize = 11.sp) },
                        placeholder = { Text("+91 98...", fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextWhitePrimary,
                            unfocusedTextColor = TextWhitePrimary,
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = TitaniumBorder
                        ),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = city,
                        onValueChange = { city = it },
                        label = { Text("City / State", fontSize = 11.sp) },
                        placeholder = { Text("Coimbatore", fontSize = 11.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextWhitePrimary,
                            unfocusedTextColor = TextWhitePrimary,
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = TitaniumBorder
                        ),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Account Role Chips
                Text(
                    text = "Account Category / Purpose:",
                    color = Color.LightGray,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    roles.take(2).forEach { r ->
                        val isSel = r == selectedRole
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSel) GoldPrimary else TitaniumCardBg,
                            border = androidx.compose.foundation.BorderStroke(
                                0.5.dp,
                                if (isSel) GoldPrimary else TitaniumBorder
                            ),
                            modifier = Modifier.weight(1f).clickable { selectedRole = r }
                        ) {
                            Text(
                                text = r,
                                color = if (isSel) Color.Black else Color.LightGray,
                                fontSize = 10.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    roles.drop(2).forEach { r ->
                        val isSel = r == selectedRole
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSel) GoldPrimary else TitaniumCardBg,
                            border = androidx.compose.foundation.BorderStroke(
                                0.5.dp,
                                if (isSel) GoldPrimary else TitaniumBorder
                            ),
                            modifier = Modifier.weight(1f).clickable { selectedRole = r }
                        ) {
                            Text(
                                text = r,
                                color = if (isSel) Color.Black else Color.LightGray,
                                fontSize = 10.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Text("Cancel", color = Color.Gray, fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            val cleanUser = username.trim().removePrefix("@")
                            val cleanName = name.trim()

                            if (cleanUser.isBlank()) {
                                validationError = "Please enter a username"
                                return@Button
                            }
                            if (cleanName.isBlank()) {
                                validationError = "Please enter your full name"
                                return@Button
                            }
                            if (email.isBlank() || !email.contains("@")) {
                                validationError = "Please enter a valid email address"
                                return@Button
                            }

                            val existing = UserAccountManager.findExistingAccount(
                                username = cleanUser,
                                email = email,
                                phone = phone
                            )
                            if (existing != null) {
                                existingAccountPopup = existing
                                validationError = "Account '@${existing.username}' already exists! Please sign in."
                                return@Button
                            }

                            val newProfile = UserAccountManager.createAccount(
                                username = cleanUser,
                                name = cleanName,
                                email = email,
                                phone = phone,
                                city = if (city.isNotBlank()) city else "Coimbatore",
                                role = selectedRole,
                                pin4Digit = "1234",
                                enableLockImmediately = false,
                                enableBiometric = false
                            )
                            onAccountCreated(newProfile)
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = Color.Black),
                        modifier = Modifier.weight(1.5f).height(44.dp).testTag("submit_create_account_button")
                    ) {
                        Text("Create Account & Save", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

package com.app.hamfilm.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.hamfilm.desktop.BlueAccent
import com.app.hamfilm.desktop.DarkCardBackground
import com.app.hamfilm.desktop.GreenAccent
import com.app.hamfilm.desktop.Res
import com.app.hamfilm.desktop.RedAccent
import com.app.hamfilm.desktop.YellowAccent
import com.app.hamfilm.desktop.net.Api
import kotlinx.coroutines.launch

enum class AuthMode { LOGIN, OTP, REGISTER }

@Composable
fun LoginScreen(
    onLoggedIn: (String) -> Unit, // token
) {
    var mode by remember { mutableStateOf(AuthMode.LOGIN) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }
    var otpIdentity by remember { mutableStateOf("") }
    var regName by remember { mutableStateOf("") }
    var regPhone by remember { mutableStateOf("") }
    var regPassword by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var info by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Box(
        Modifier
            .fillMaxSize()
            .background(DarkCardBackground)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.width(400.dp)
        ) {
            Res.logo?.let {
                Image(
                    bitmap = it,
                    contentDescription = "لوگو همفیلم",
                    modifier = Modifier.size(96.dp).clip(RoundedCornerShape(22.dp))
                )
            }
            Text("همفیلم", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = YellowAccent)
            Text(
                "فیلم رو با دوستات هم‌زمان ببینید",
                fontSize = 14.sp, color = com.app.hamfilm.desktop.MediumGrayText,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            when (mode) {
                AuthMode.LOGIN -> {
                    HamTextField(email, "ایمیل", { email = it }, keyboard = KeyboardType.Email)
                    HamTextField(password, "رمز عبور", { password = it }, isPassword = true)
                    Button(
                        onClick = {
                            loading = true; error = ""; info = ""
                            scope.launch {
                                Api.login(email, password)
                                    .onSuccess { onLoggedIn(it) }
                                    .onFailure { error = it.message ?: "خطا" }
                                loading = false
                            }
                        },
                        enabled = email.isNotBlank() && password.isNotBlank() && !loading,
                        colors = ButtonDefaults.buttonColors(containerColor = YellowAccent),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Text("ورود", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10131A))
                    }
                    OutlinedButton(
                        onClick = { mode = AuthMode.OTP; error = ""; info = "" },
                        modifier = Modifier.fillMaxWidth().height(44.dp)
                    ) { Text("ورود با کد یکبارمصرف", color = BlueAccent) }
                }

                AuthMode.OTP -> {
                    HamTextField(otpIdentity, "ایمیل یا شماره موبایل", { otpIdentity = it })
                    if (info.isNotBlank()) {
                        HamTextField(otpCode, "کد ۶ رقمی", {
                            otpCode = it.filter { c -> c.isDigit() }.take(6)
                        })
                    }
                    Button(
                        onClick = {
                            loading = true; error = ""
                            scope.launch {
                                if (info.isBlank()) {
                                    Api.sendLoginOtp(otpIdentity)
                                        .onSuccess { info = "کد ارسال شد"; }
                                        .onFailure { error = it.message ?: "خطا" }
                                } else {
                                    Api.verifyLoginOtp(otpIdentity, otpCode)
                                        .onSuccess { onLoggedIn(it) }
                                        .onFailure { error = it.message ?: "خطا" }
                                }
                                loading = false
                            }
                        },
                        enabled = otpIdentity.isNotBlank() && !loading &&
                                (info.isBlank() || otpCode.length == 6),
                        colors = ButtonDefaults.buttonColors(containerColor = GreenAccent),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color(0xFF10131A))
                        else Text(if (info.isBlank()) "ارسال کد" else "تأیید کد", color = Color(0xFF10131A), fontWeight = FontWeight.Bold)
                    }
                }

                AuthMode.REGISTER -> {
                    HamTextField(regName, "نام", { regName = it })
                    HamTextField(regPhone, "شماره موبایل (۱۱ رقم — 09...)", {
                        regPhone = it.filter { c -> c.isDigit() }.take(11)
                    }, keyboard = KeyboardType.Phone)
                    HamTextField(regPassword, "رمز عبور", { regPassword = it }, isPassword = true)
                    Button(
                        onClick = {
                            loading = true; error = ""
                            scope.launch {
                                Api.register(regName, regPhone, regPassword)
                                    .onSuccess { onLoggedIn(it.token) }
                                    .onFailure { error = it.message ?: "خطا" }
                                loading = false
                            }
                        },
                        enabled = regName.isNotBlank() && regPhone.length == 11 &&
                                regPassword.length >= 4 && !loading,
                        colors = ButtonDefaults.buttonColors(containerColor = YellowAccent),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color(0xFF10131A))
                        else Text("ثبت‌نام", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10131A))
                    }
                }
            }

            if (error.isNotBlank()) Text(error, color = RedAccent, fontSize = 13.sp, textAlign = TextAlign.Center)
            if (info.isNotBlank() && error.isBlank()) Text(info, color = GreenAccent, fontSize = 13.sp)

            TextButton(onClick = {
                mode = if (mode == AuthMode.REGISTER) AuthMode.LOGIN else AuthMode.REGISTER
                error = ""; info = ""
            }) {
                Text(
                    if (mode == AuthMode.REGISTER) "قبلاً ثبت‌نام کرده‌اید؟ ورود" else "حساب ندارید؟ ثبت‌نام",
                    color = BlueAccent, fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
fun HamTextField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 13.sp) },
        singleLine = true,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = com.app.hamfilm.desktop.LightGrayText,
            unfocusedTextColor = com.app.hamfilm.desktop.LightGrayText,
            focusedBorderColor = YellowAccent,
            unfocusedBorderColor = com.app.hamfilm.desktop.BorderGray,
            cursorColor = YellowAccent,
            focusedLabelColor = YellowAccent,
            unfocusedLabelColor = com.app.hamfilm.desktop.DarkGrayText
        ),
        modifier = modifier.fillMaxWidth()
    )
}

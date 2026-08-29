package com.app.bebinim.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.bebinim.R
import com.app.bebinim.ui.components.ModernTextField
import com.app.bebinim.ui.components.NoInternetDialog
import com.app.bebinim.ui.theme.CyanAccent
import com.app.bebinim.ui.theme.GreenAccent
import com.app.bebinim.ui.theme.LightGrayText
import com.app.bebinim.ui.theme.MediumGrayText
import com.app.bebinim.ui.theme.RedAccent
import com.app.bebinim.ui.theme.WhiteText
import com.app.bebinim.ui.theme.YellowAccent
import com.app.bebinim.viewmodel.AuthState
import com.app.bebinim.viewmodel.AuthViewModel

@Composable
fun LoginScreen(
    onNavigateToRegister: () -> Unit,
    onLoginSuccess: () -> Unit,
    authViewModel: AuthViewModel
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }
    var loginMode by remember { mutableStateOf("password") } // password | otp
    var passwordVisible by remember { mutableStateOf(false) }

    val authState by authViewModel.authState.collectAsState()
    val showNoInternet by authViewModel.showNoInternetDialog.collectAsState()

    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.OtpSent -> loginMode = "otp"
            is AuthState.Success -> {
                authViewModel.resetState()
                onLoginSuccess()
            }
            else -> {}
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(48.dp))

            // Back (RTL)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                TextButton(onClick = onNavigateToRegister) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Color(0xFF4A5568), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("بازگشت", fontSize = 13.sp, color = Color(0xFF4A5568))
                }
            }

            Image(
                painter = painterResource(R.drawable.logo),
                contentDescription = "لوگو همفیلم",
                modifier = Modifier.size(90.dp)
            )
            Text("تماشای گروهی با دوستان", fontSize = 15.sp, color = MediumGrayText)

            Spacer(Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1928)),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("ورود به حساب", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = WhiteText)
                    Text("خوش برگشتید! وارد حسابتان شوید", fontSize = 13.sp, color = Color(0xFF5B6B84))
                    Spacer(Modifier.height(20.dp))

                    ModernTextField(
                        value = email,
                        onValueChange = { email = it },
                        placeholder = "ایمیل",
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                        enabled = authState !is AuthState.Loading
                    )

                    Spacer(Modifier.height(12.dp))

                    // mode chooser
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { authViewModel.sendLoginOtp(email) },
                            enabled = email.isNotBlank() && authState !is AuthState.Loading,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0x1AFFC500)
                            )
                        ) {
                            Icon(Icons.Filled.Sms, null, tint = if (email.isNotBlank()) YellowAccent else Color(0xFF5B6B84), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(6.dp))
                            Text(
                                "ورود با کد یکبار مصرف",
                                fontSize = 11.sp,
                                color = if (email.isNotBlank()) YellowAccent else Color(0xFF5B6B84)
                            )
                        }
                        OutlinedButton(
                            onClick = { loginMode = "password" },
                            enabled = email.isNotBlank(),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.Key, null, tint = Color(0xFF4A5568), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("ورود با رمز عبور", fontSize = 11.sp, color = Color(0xFF4A5568))
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    when (loginMode) {
                        "password" -> {
                            ModernTextField(
                                value = password,
                                onValueChange = { password = it },
                                placeholder = "رمز عبور",
                                leadingIcon = Icons.Filled.Lock,
                                isPassword = true,
                                passwordVisible = passwordVisible,
                                onPasswordToggle = { passwordVisible = !passwordVisible },
                                imeAction = ImeAction.Done,
                                onImeAction = { authViewModel.login(email, password) },
                                enabled = authState !is AuthState.Loading
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = { authViewModel.login(email, password) },
                                enabled = email.isNotBlank() && password.isNotBlank() && authState !is AuthState.Loading,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = YellowAccent)
                            ) {
                                if (authState is AuthState.Loading) {
                                    CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.Black)
                                } else {
                                    Text("ورود", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (email.isNotBlank() && password.isNotBlank()) Color(0xFF050C1A) else Color(0xFF718096))
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("رمز عبور خود را فراموش کرده‌اید؟", fontSize = 13.sp, color = Color(0xFFFF8A00))
                        }

                        "otp" -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.CheckCircle, null, tint = GreenAccent, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(6.dp))
                                Text("کد ارسال شد", fontSize = 12.sp, color = GreenAccent)
                            }
                            Spacer(Modifier.height(10.dp))
                            ModernTextField(
                                value = otpCode,
                                onValueChange = { otpCode = it.filter { c -> c.isDigit() }.take(6) },
                                placeholder = "کد تایید پیامکی",
                                leadingIcon = Icons.Filled.Lock,
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done,
                                onImeAction = { authViewModel.verifyLoginOtp(email, otpCode) },
                                enabled = authState !is AuthState.Loading
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = { authViewModel.verifyLoginOtp(email, otpCode) },
                                enabled = otpCode.isNotBlank() && authState !is AuthState.Loading,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = GreenAccent)
                            ) {
                                if (authState is AuthState.Loading) {
                                    CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White)
                                } else {
                                    Icon(Icons.Filled.Check, null, tint = WhiteText, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.size(6.dp))
                                    Text("تایید و ورود", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (otpCode.isNotBlank()) WhiteText else Color(0xFF718096))
                                }
                            }
                        }
                    }

                    // error message
                    (authState as? AuthState.Error)?.let { state ->
                        Spacer(Modifier.height(10.dp))
                        Text(state.message, fontSize = 13.sp, color = RedAccent)
                    }

                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("حساب کاربری ندارید؟", fontSize = 14.sp, color = MediumGrayText)
                        TextButton(onClick = onNavigateToRegister) {
                            Text("ثبت‌نام کنید", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF8A00))
                        }
                    }
                }
            }
        }
    }

    NoInternetDialog(visible = showNoInternet, onDismiss = { authViewModel.dismissNoInternetDialog() })
}

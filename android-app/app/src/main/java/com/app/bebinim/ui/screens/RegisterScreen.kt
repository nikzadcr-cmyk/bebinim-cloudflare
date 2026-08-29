package com.app.bebinim.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.bebinim.R
import com.app.bebinim.ui.components.ModernTextField
import com.app.bebinim.ui.theme.MediumGrayText
import com.app.bebinim.ui.theme.RedAccent
import com.app.bebinim.ui.theme.WhiteText
import com.app.bebinim.ui.theme.YellowAccent
import com.app.bebinim.viewmodel.AuthState
import com.app.bebinim.viewmodel.AuthViewModel

@Composable
fun RegisterScreen(
    onNavigateToLogin: () -> Unit,
    onRegisterSuccess: () -> Unit,
    authViewModel: AuthViewModel
) {
    var phoneNumber by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    val authState by authViewModel.authState.collectAsState()

    val phoneValid = Regex("^(\\+98|0)?9\\d{9}$")
        .matches(phoneNumber.replace(Regex("[\\s\\-]"), ""))
    val isFormValid = phoneValid && password.length >= 6 && password == confirmPassword

    LaunchedEffect(authState) {
        if (authState is AuthState.Success) {
            authViewModel.resetState()
            onRegisterSuccess()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(40.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            IconButton(
                onClick = onNavigateToLogin,
                modifier = Modifier.size(44.dp),
                colors = androidx.compose.material3.IconButtonDefaults.iconButtonColors(
                    containerColor = Color(0x141C2735)
                )
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, "بازگشت", tint = Color(0xFF4A5568))
            }
        }

        Spacer(Modifier.height(8.dp))
        Image(painter = painterResource(R.drawable.logo), contentDescription = "لوگو همفیلم", modifier = Modifier.size(70.dp))
        Text("ساخت حساب کاربری", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = WhiteText)
        Text("به جمع همفیلمی‌ها خوش آمدید", fontSize = 14.sp, color = MediumGrayText, letterSpacing = 0.5.sp)

        Spacer(Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1928))
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                ModernTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it.filter { c -> c.isDigit() || c == '+' } },
                    placeholder = "شماره موبایل",
                    leadingIcon = Icons.Filled.Person,
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Next,
                    isError = phoneNumber.isNotBlank() && !phoneValid
                )
                AnimatedVisibility(visible = phoneNumber.isNotBlank() && !phoneValid) {
                    Row(
                        modifier = Modifier.padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.ErrorOutline, null, tint = RedAccent, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("شماره تلفن ایرانی معتبر وارد کنید", fontSize = 11.sp, color = RedAccent)
                    }
                }

                Spacer(Modifier.height(14.dp))
                ModernTextField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = "رمز عبور",
                    leadingIcon = Icons.Filled.Lock,
                    isPassword = true,
                    passwordVisible = passwordVisible,
                    onPasswordToggle = { passwordVisible = !passwordVisible },
                    imeAction = ImeAction.Next
                )

                Spacer(Modifier.height(14.dp))
                ModernTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    placeholder = "تکرار رمز عبور",
                    leadingIcon = Icons.Filled.Lock,
                    isPassword = true,
                    passwordVisible = confirmPasswordVisible,
                    onPasswordToggle = { confirmPasswordVisible = !confirmPasswordVisible },
                    imeAction = ImeAction.Done,
                    onImeAction = { if (isFormValid) authViewModel.register("", phoneNumber, password) },
                    isError = confirmPassword.isNotBlank() && password != confirmPassword
                )
                AnimatedVisibility(visible = confirmPassword.isNotBlank() && password != confirmPassword) {
                    Row(
                        modifier = Modifier.padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.ErrorOutline, null, tint = RedAccent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("رمز عبور مطابقت ندارد", fontSize = 12.sp, color = RedAccent)
                    }
                }

                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = {
                        authViewModel.register("", phoneNumber, password)
                    },
                    enabled = isFormValid && authState !is AuthState.Loading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = YellowAccent)
                ) {
                    if (authState is AuthState.Loading) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.Black)
                    } else {
                        Text(
                            "ثبت‌نام",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isFormValid) Color(0xFF050C1A) else Color(0xFF718096)
                        )
                    }
                }

                (authState as? AuthState.Error)?.let { state ->
                    Spacer(Modifier.height(10.dp))
                    Text(state.message, fontSize = 13.sp, color = RedAccent)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("قبلاً ثبت‌نام کرده‌اید؟", fontSize = 14.sp, color = MediumGrayText)
            TextButton(onClick = onNavigateToLogin) {
                Text("وارد شوید", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF8A00))
            }
        }
    }
}

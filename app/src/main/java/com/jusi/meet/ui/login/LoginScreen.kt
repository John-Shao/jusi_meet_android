package com.jusi.meet.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jusi.meet.JusiMeetApp
import com.jusi.meet.R

@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as JusiMeetApp
    val viewModel: LoginViewModel = viewModel(factory = LoginViewModel.Factory(app))
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold { padding ->
        LoginContent(
            padding = padding,
            state = state,
            onPhoneChange = viewModel::onPhoneChange,
            onOtpChange = viewModel::onOtpChange,
            onSendOtp = viewModel::sendOtp,
            onVerify = { viewModel.verifyOtp(onSuccess = onLoggedIn) },
        )
    }
}

@Composable
private fun LoginContent(
    padding: PaddingValues,
    state: LoginUiState,
    onPhoneChange: (String) -> Unit,
    onOtpChange: (String) -> Unit,
    onSendOtp: () -> Unit,
    onVerify: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.login_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.login_subtitle),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = state.phone,
            onValueChange = onPhoneChange,
            label = { Text(stringResource(R.string.login_phone_label)) },
            placeholder = { Text(stringResource(R.string.login_phone_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        if (state.codeSent) {
            OutlinedTextField(
                value = state.otp,
                onValueChange = onOtpChange,
                label = { Text(stringResource(R.string.login_otp_label)) },
                placeholder = { Text(stringResource(R.string.login_otp_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
        }

        TextButton(
            onClick = onSendOtp,
            enabled = !state.isSendingOtp && state.resendCooldown == 0,
            modifier = Modifier.fillMaxWidth(),
        ) {
            val label = when {
                state.resendCooldown > 0 ->
                    stringResource(R.string.login_resend_otp, state.resendCooldown)
                state.isSendingOtp -> stringResource(R.string.login_send_otp) + "…"
                else -> stringResource(R.string.login_send_otp)
            }
            Text(label)
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = onVerify,
            enabled = state.codeSent && !state.isVerifying,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.isVerifying) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(18.dp))
            } else {
                Text(stringResource(R.string.login_verify))
            }
        }

        state.errorMessage?.let { rawMessage ->
            Spacer(Modifier.height(16.dp))
            val text = when (rawMessage) {
                LoginViewModel.ErrorKey.PHONE_FORMAT.name -> stringResource(R.string.login_error_phone_format)
                LoginViewModel.ErrorKey.OTP_FORMAT.name -> stringResource(R.string.login_error_otp_format)
                LoginViewModel.ErrorKey.NETWORK.name -> stringResource(R.string.error_network)
                LoginViewModel.ErrorKey.UNKNOWN.name -> stringResource(R.string.error_unknown)
                else -> rawMessage
            }
            Text(text, color = MaterialTheme.colorScheme.error)
        }
    }
}

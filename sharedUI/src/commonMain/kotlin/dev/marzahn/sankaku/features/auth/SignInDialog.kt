package dev.marzahn.sankaku.features.auth

import androidx.compose.foundation.layout.Arrangement
import dev.marzahn.sankaku.ui.components.ButtonHint
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.selectAll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldLabelPosition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.google.fonts.icons.error
import com.google.fonts.icons.visibility
import com.google.fonts.icons.visibility_off
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.marzahn.sankaku.auth.TokenStorageStatus
import dev.marzahn.sankaku.NoAppForLink
import dev.marzahn.sankaku.OpenUrlResult
import dev.marzahn.sankaku.platformOpenUrl
import dev.marzahn.sankaku.ui.components.AppLoadingIndicator
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

internal fun termsNotice(storage: TokenStorageStatus): String {
  val kept = when (storage) {
    TokenStorageStatus.Keychain -> "keeps your session in the system keychain"
    TokenStorageStatus.Encrypted -> "keeps your session encrypted on this device"
    TokenStorageStatus.InMemory -> "keeps your session only while the app is open"
  }
  return "Unofficial client. Sankaku's terms prohibit 'unauthorized User software'; your account or IP may be " +
    "restricted. The app $kept and never stores your password."
}

internal const val LoginFieldTag = "sign-in-login"
internal const val PasswordFieldTag = "sign-in-password"

private val FieldLabelPosition = TextFieldLabelPosition.Attached(alwaysMinimize = true)

internal const val ShowPasswordTag = "sign-in-show-password"
internal const val SignInButtonTag = "sign-in-button"

@Composable
internal fun SignInFields(
  login: TextFieldState,
  password: TextFieldState,
  enabled: Boolean,
  rejection: String? = null,
  loginModifier: Modifier = Modifier,
  passwordModifier: Modifier = Modifier,
) {
  var passwordShown by remember { mutableStateOf(false) }
  val inError = rejection != null
  OutlinedTextField(
    state = login,
    labelPosition = FieldLabelPosition,
    label = { Text("Username or email") },
    lineLimits = TextFieldLineLimits.SingleLine,
    enabled = enabled,
    isError = inError,
    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
    modifier = Modifier
      .fillMaxWidth()
      .testTag(LoginFieldTag)
      .semantics { contentType = ContentType.Username + ContentType.EmailAddress }
      .then(loginModifier),
  )
  OutlinedSecureTextField(
    state = password,
    labelPosition = FieldLabelPosition,
    label = { Text("Password") },
    enabled = enabled,
    isError = inError,
    textObfuscationMode = if (passwordShown) TextObfuscationMode.Visible else TextObfuscationMode.RevealLastTyped,
    trailingIcon = {
      val label = if (passwordShown) "Hide password" else "Show password"
      ButtonHint(label) {
        IconButton(onClick = { passwordShown = !passwordShown }, modifier = Modifier.testTag(ShowPasswordTag)) {
          Icon(if (passwordShown) visibility_off else visibility, contentDescription = label)
        }
      }
    },
    supportingText = rejection?.let {
      {
        Row(
          horizontalArrangement = Arrangement.spacedBy(4.dp),
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        ) {
          Icon(error, contentDescription = null, modifier = Modifier.size(16.dp))
          Text(it)
        }
      }
    },
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
    modifier = Modifier
      .fillMaxWidth()
      .testTag(PasswordFieldTag)
      .semantics { contentType = ContentType.Password }
      .then(passwordModifier),
  )
}

@Composable
internal fun SignInButton(enabled: Boolean, submitting: Boolean, onClick: () -> Unit) {
  Button(
    onClick = { if (!submitting) onClick() },
    enabled = enabled,
    modifier = Modifier.fillMaxWidth().height(48.dp).testTag(SignInButtonTag),
  ) {
    if (submitting) {
      AppLoadingIndicator(size = 32.dp, color = MaterialTheme.colorScheme.onPrimary)
    } else {
      Text("Sign in")
    }
  }
}

internal const val SignUpUrl = "https://login.sankakucomplex.com/registration"

internal const val CreateAccountTag = "sign-in-create-account"

@Composable
internal fun CreateAccountLink(open: (String) -> OpenUrlResult = ::platformOpenUrl) {
  var failed by remember { mutableStateOf(false) }
  Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
    TextButton(
      onClick = { failed = open(SignUpUrl) == OpenUrlResult.Failed },
      modifier = Modifier.fillMaxWidth().testTag(CreateAccountTag),
    ) {
      Text("Create an account")
    }
    if (failed) {
      Text(
        NoAppForLink,
        Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
      )
    }
  }
}

@Composable
fun SignInDialog(controller: SignInController = koinInject(), storage: TokenStorageStatus = koinInject()) {
  val isOpen by controller.isOpen.collectAsStateWithLifecycle()
  val reason by controller.reason.collectAsStateWithLifecycle()
  if (!isOpen) return

  val viewModel = koinViewModel<SignInViewModel>()
  val state by viewModel.state.collectAsStateWithLifecycle()
  val login = rememberTextFieldState()
  val password = remember { TextFieldState() }
  val focus = remember { FocusRequester() }
  val passwordFocus = remember { FocusRequester() }
  LaunchedEffect(Unit) { focus.requestFocus() }
  LaunchedEffect(state.rejections) {
    if (state.rejections > 0) {
      passwordFocus.requestFocus()
      password.edit { selectAll() }
    }
  }

  val submit = { viewModel.submit(login.text.toString(), password.text.toString()) }
  val onEnter = Modifier.onPreviewKeyEvent {
    if (it.type == KeyEventType.KeyDown && (it.key == Key.Enter || it.key == Key.NumPadEnter)) {
      submit()
      true
    } else {
      false
    }
  }

  SignInPresentation(
    reason = reason,
    onDismiss = viewModel::cancel,
    body = {
      SignInFields(
        login = login,
        password = password,
        enabled = !state.submitting,
        rejection = state.error.takeIf { state.fieldsInError },
        loginModifier = Modifier.focusRequester(focus).then(onEnter),
        passwordModifier = Modifier.focusRequester(passwordFocus).then(onEnter),
      )
      if (!state.fieldsInError) {
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        state.errorDetail?.let {
          Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
      Text(
        termsNotice(storage),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    },
    confirm = {
      SignInButton(
        enabled = login.text.isNotBlank() && password.text.isNotEmpty(),
        submitting = state.submitting,
        onClick = submit,
      )
      CreateAccountLink()
    },
    dismiss = { TextButton(onClick = viewModel::cancel) { Text("Cancel") } },
  )
}

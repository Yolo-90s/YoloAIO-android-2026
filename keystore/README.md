# Release keystore

`yolo-release.jks` is the signing key for the release builds of Yolo AIO.

## ⚠️ Critical — back this up

**Lose this file → you can never publish an update of this app under the same identity.** Google Play (and every other Android distribution channel) keys all app updates to the certificate inside this file.

Recommended backup:
- Upload `yolo-release.jks` to your Google Drive / Dropbox / OneDrive **AND** to a password manager (1Password, Bitwarden, etc.) as an attachment.
- Don't commit it to a public git repo.

## Credentials

| Field | Value |
|---|---|
| Keystore file | `keystore/yolo-release.jks` |
| Store password | `YoloAIO2026!` |
| Key alias | `yolo` |
| Key password | `YoloAIO2026!` |
| Validity | 10,000 days (~27 years) |
| Algorithm | RSA 2048-bit, SHA256withRSA |
| DN | `CN=Yolo AIO, OU=Personal, O=Personal, L=Unknown, ST=Unknown, C=US` |

## Changing the password later

```
keytool -storepasswd -keystore yolo-release.jks
keytool -keypasswd -alias yolo -keystore yolo-release.jks
```

If you change passwords, also update `local.properties` (`YOLO_KEYSTORE_PASSWORD` and `YOLO_KEY_PASSWORD`).

## Inspecting

```
keytool -list -v -keystore yolo-release.jks -storepass YoloAIO2026!
```

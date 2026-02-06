# Lawnchair verification

Lawnchair apk are cryptographically signed and can be verified using two verifications system.
1. GitHub or SLSA attestations (Starting with Lawnchair 15 Beta 1)
2. SHA256 of android app certificate

## SLSA Attestation (Starting with Lawnchair 15 Beta 1)

Every release of Lawnchair with the exception of Nightly is attested and verified with SLSA provenance. This repository meet the requirements of SLSA-Level 2 compliance

> [!NOTE]
> It is possible to verify without GitHub CLI by cross-referencing check from 
> [GitHub Attestation][github-attestation] with [Sigstore Rekor][sigstore-rekor]

1. Install GitHub CLI
2. Download the APK and attestation from [GitHub Attestation][github-attestation]
3. Run `gh attestation verify APK -R LawnchairLauncher/lawnchair`, replace {APK} with the 
   actual APK file
4. Done

## Android App Certificate

Lawnchair have two app certificates:
* Google Play: 
  `47:AC:92:63:1C:60:35:13:CC:8D:26:DD:9C:FF:E0:71:9A:8B:36:55:44:DC:CE:C2:09:58:24:EC:25:61:20:A7`
* Elsewhere:   
  `74:7C:36:45:B3:57:25:8B:2E:23:E8:51:E5:3C:96:74:7F:E0:AD:D0:07:E5:BA:2C:D9:7E:8C:85:57:2E:4D:C5`

On Android, using a verification app like [AppVerifier][3p-appverifier] can ease up the verifying process.

[github-attestation]: https://github.com/LawnchairLauncher/lawnchair/attestations
[sigstore-rekor]: https://search.sigstore.dev/
[3p-appverifier]: https://github.com/soupslurpr/AppVerifier

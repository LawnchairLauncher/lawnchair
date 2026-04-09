# Verifying your Lawnchair installation

Lawnchair's APKs are cryptographically signed and can be verified using two systems:

1. GitHub / SLSA attestations (Starting with Lawnchair 15 Beta 1)
2. SHA-256 Android app certificate fingerprints

## SLSA attestation

Every Lawnchair release, starting with Lawnchair 15 Beta 1 (except Nightly builds), is attested and
verified with SLSA provenance.
This repository meets the requirements for SLSA Level 2 compliance.

> [!NOTE]
> You can verify without using the GitHub CLI by cross-referencing checks from
> [GitHub Attestations][github-attestation] with [Sigstore Rekor][sigstore-rekor].

1. Install the [GitHub CLI](https://cli.github.com/).
2. Download the APK and its attestation from [GitHub Attestations][github-attestation].
3. Run `gh attestation verify {APK} -R LawnchairLauncher/lawnchair`, replacing `{APK}` with the path
   to the downloaded APK file.

## Android App Certificate

Lawnchair uses two app certificates, which can be verified by tools such
as [AppVerifier][3p-appverifier]:

* **Google Play**:
  `47:AC:92:63:1C:60:35:13:CC:8D:26:DD:9C:FF:E0:71:9A:8B:36:55:44:DC:CE:C2:09:58:24:EC:25:61:20:A7`
* **Direct Downloads (Elsewhere)**:   
  `74:7C:36:45:B3:57:25:8B:2E:23:E8:51:E5:3C:96:74:7F:E0:AD:D0:07:E5:BA:2C:D9:7E:8C:85:57:2E:4D:C5`

[github-attestation]: https://github.com/LawnchairLauncher/lawnchair/attestations
[sigstore-rekor]: https://search.sigstore.dev/
[3p-appverifier]: https://github.com/soupslurpr/AppVerifier

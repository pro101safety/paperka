# papera
The application contains extracts from the current regulations 'PAPERKA on labor protection' is a free mobile application for specialists, labor protection engineers, officials responsible for the organization of labor protection, as well as those working on the territory of the Republic of Belarus.
Does not require internet.
The appendix contains extracts from the current regulatory legal acts related to labor protection, which I use daily in my professional activities.

IMPORTANT: the given texts of documents are not the edition of normative legal acts from the reference data bank of legal information!

## Signing (release)

Create a `keystore.properties` file (ignored by git) using
`keystore.properties.example` as a template, and point `storeFile`
to your local keystore path. If `keystore.properties` is missing,
release builds fall back to debug signing for local testing.
On Windows, prefer forward slashes (e.g. `D:/DEV/keys/release.jks`).

If you build a signed APK/BAB from Android Studio and see a
`validateSigningRelease` error, update the keystore path in the
signing dialog or remove the injected signing override.

# apps/

Put the APKs under test here. `*.apk` files are git-ignored, so binaries never bloat the repository.

| Variant    | File                        | Configured in                                        |
|------------|-----------------------------|------------------------------------------------------|
| variantA   | `apps/ezazi-variantA.apk`   | `src/test/resources/config/variants/variantA.properties` |
| variantB   | `apps/ezazi-variantB.apk`   | `src/test/resources/config/variants/variantB.properties` |

To test another build without renaming it, pass `-Dapp.path=D:/path/to/build.apk`.

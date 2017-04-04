# kotlin-version-changer
A tool for patching Gradle build scripts with newer Kotlin versions, meant for testing

### Usage

1. Download a binary distribution from the [releases page](https://github.com/h0tk3y/kotlin-version-changer/releases)
2. Unpack it and run `bin/kotlin-version-changer` or `bin/kotlin-version-changer.bat`, depending on your OS, with the parameters:

    * `--project` -- project root directory, e.g. `C:\kotlin-gradle-test\`
    * `--version` -- target Kotlin version, e.g. `1.1.2-eap-44`
    * `--destination` -- destination to copy the project, process in place if not provided
    * `--repository` -- repository to add to buildscript and project, one of `DEV`, `EAP`, `LOCAL`
 
    Example:
    ```
    kotlin-version-changer.bat --project C:\kotlin-gradle-test\ --version 1.1.2-eap-44 --repository EAP
    ```
    
This tool does not analyze extension variables like `ext.kotlin_version`, instead, the versions are changed directly in the dependency declarations.
Also, this tool is not meant for the projects you work with, because it does not follow Gradle idioms.

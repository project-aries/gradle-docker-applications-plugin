### Version 0.9.9 (TBA)

### Version 0.9.8 (July 8, 2018)
* Bump gradle-docker-plugin to 3.4.3

### Version 0.9.7 (July 7, 2018)
* Bump gradle-docker-plugin to 3.4.2

### Version 0.9.6 (July 1, 2018)
* Bump gradle-docker-plugin to 3.4.1

### Version 0.9.5 (June 25, 2018)
* Rename project and package structure to be `gradle-docker-applications-plugin`
* Only execute *CopyFiles tasks if have more than 0 `files` configs.

### Version 0.9.4 (June 23, 2018)
* Both the `main` and `data` container can now configure an optional `files` task to allow for an arbitrary number of files
to be added to either container BEFORE we attempt to start the `main` container.

### Version 0.9.3 (June 17, 2018)
* The 'main' container can now configure an optional 'exec' task to be run once liveness has been attained.

### Version 0.9.2 (June 17, 2018)
* Fix for `data` container not getting properly configured.
* Bump gradle-docker-plugin to 3.3.5

### Version 0.9.1 (June 16, 2018)
* Bump gradle-docker-plugin to 3.3.4

### Version 0.9.0 (June 12, 2018)
* Initial project release that allows users to define up to N dockerized applications with each gettin their own **Up**, **Stop**, and **Down** tasks.

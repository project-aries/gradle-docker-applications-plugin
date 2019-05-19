### Version 1.4.0 (May 18, 2019)
* Introduction of `DockerManageContainer` task.
* Overall refactor of internal code to use less gradle tasks and take advantage of `DockerManageContainer` task.
* All `Up` tasks will now have a list of `SummaryReport` objects attached to them for downstreaming querying of running applications.

### Version 1.3.0 (May 10, 2019)
* `main` and `data` containers now lazily initialized.
* application dsl gained `lock` and `dependsOn`.

### Version 1.2.0 (May 1, 2019)
* Each application gets its own private network stack.
* application dsl gained `network` and `disableNetwork`.

### Version 1.1.0 (July 27, 2018)
* All configs will now be applied/configured within its backing tasks doFirst block.
* Bump gradle-docker-plugin to 3.5.0

### Version 1.0.2 (July 19, 2018)
* Removal of application extension points which were created after evaluation and not very useful.
* Valid container failure checking will now take into account exception messages which contain the phrase `not running`.

### Version 1.0.1 (July 16, 2018)
* Account for when plugin is applied to a script which is not the root script.

### Version 1.0.0 (July 15, 2018)
* Bump gradle-docker-plugin to 3.4.4

### Version 0.9.9 (July 14, 2018)
* If 'id' is defined tha will take the place of the entire container name instead of being a concatenation of if and the image being used.
* Add 'ConflictException' to list of regex's we will check should container not be present or running.

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

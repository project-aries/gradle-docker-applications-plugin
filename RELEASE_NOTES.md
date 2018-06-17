### Version 0.9.3 (June 17, 2018)

* The 'main' container can now configure an optional 'exec' task to be run once liveness has been attained.

### Version 0.9.2 (June 17, 2018)

* Fix for `data` container not getting properly configured.
* Bump gradle-docker-plugin to 3.3.5

### Version 0.9.1 (June 16, 2018)

* Bump gradle-docker-plugin to 3.3.4

### Version 0.9.0 (June 12, 2018)

* Initial project release that allows users to define up to N dockerized applications with each gettin their own **Up**, **Stop**, and **Down** tasks.

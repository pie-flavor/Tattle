# Tattle

Tattle is a plugin that players can use to send reports to staff.

### Commands

`/tt new <explanation>` : Creates a new complaint. Your location is also recorded.
`/tt list` : Lists all complaints that you have created. You can view and delete them from here.
`/tt admin` : Lists all complaints that players have made. You can view and delete them from here, as well as teleport to the recorded location.

You can also use `/tt +`, `/tt ?`, and `/tt !` respectively.

This plugin also supports `/sponge plugins reload`, and will reload both configs when the command is run.

### Permissions

`tattle.admin.use` : Allows use of `/tt admin`.

### Configuration

You can configure the language in `config.conf`, the paths are self-explanatory.

### Changelog

1.0.0: Reported.
1.1.0: Added languages and reloading.
1.1.1: Fixed a bug with file loading.

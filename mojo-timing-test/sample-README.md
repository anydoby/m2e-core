# m2e slow builder test

Test project for two m2e changes (branch `mojo-timing` in `../m2e-core`):

1. The build stops between mojos when you save (and optionally interrupts the running mojo).
2. m2e warns about slow mojos in incremental builds and offers a quick fix.

Each incremental build runs two slow plugins, both mapped with `runOnIncremental=true`:

| Plugin | Execution | Time |
|---|---|---|
| maven-antrun-plugin:run | generate-build-info | 3 s, writes `target/classes/build-info.txt` |
| exec-maven-plugin:exec | check-licenses | 2 s (`sleep 2`) |

## Try it

1. File > Import > Existing Maven Projects. Keep Project > Build Automatically on.
2. Edit `Greeter.java` and save several times fast. The Progress view shows
   "Invoking 'Maven Project Builder'". A save waits at most for the current plugin
   (up to 3 s), not for the full build.
3. Optional: Preferences > Maven > select "Interrupt a running Maven plugin execution ..."
   (shown because `eclipse.ini` has `-Dm2e.showExperimentalFeatures=true`).
   Then a save waits about 250 ms.
4. After 3 saves, `pom.xml` gets two warnings (Problems view), one at each slow plugin.
5. Ctrl+1 on a warning in `pom.xml` > "Run goal run on full builds only (pom.xml)".
   The plugin then runs only on Project > Clean, and its warning goes away after the next save.
   The workspace mapping quick fix is not offered here, because this `pom.xml` maps the plugins
   (a pom mapping always wins over the workspace mapping).
6. Run `GreeterTest` (Run As > JUnit Test). A launch waits for the build, so it still waits
   for the slow plugins until you apply the quick fix.

To undo the m2e patch: `sh patch/patch-m2e-core.sh ~/eclipse/2023/eclipse/plugins --restore`,
then restart Eclipse.

package org.h2.build;

import java.io.File;

/**
 * The implementation of the pure Java build.
 */
public class Build extends BuildBase {

    public static void main(String[] args) {
        new Build().run(args);
    }

    public void all() {
        clean();
        compile();
        jar();
    }

    public void clean() {
        mkdir("bin");
        delete("temp");
        mkdir("temp");
    }

    public void compile() {
        clean();
        javac(args("-d", "temp", "-sourcepath", "src/main"), files("src"));

        FileList files = files("src/main").exclude("*.java").exclude("*.launch");
        copy("temp", files, "src/main");

        manifest("org.mp3transform.awt.Player");
    }

    private void manifest(String mainClassName) {
        String manifest = new String(readFile(new File("src/main/META-INF/MANIFEST.MF")));
        manifest = replaceAll(manifest, "${buildJdk}", getJavaSpecVersion());
        String createdBy = System.getProperty("java.runtime.version") + " (" + System.getProperty("java.vm.vendor")
                + ")";
        manifest = replaceAll(manifest, "${createdBy}", createdBy);
        String mainClassTag = manifest == null ? "" : "Main-Class: " + mainClassName;
        manifest = replaceAll(manifest, "${mainClassTag}", mainClassTag);
        writeFile(new File("temp/META-INF/MANIFEST.MF"), manifest.getBytes());
    }

    public void jar() {
        FileList files = files("temp").exclude("temp/org/mp3transform/build/*");
        jar("bin/mp3transform.jar", files, "temp");
        delete("temp");
    }

    public void zip() {
        FileList files = files(".").exclude("./bin/org/*").exclude("./srcTest/*");
        zip("../mp3-transform-0.81.zip", files, ".", false, true);
    }

}

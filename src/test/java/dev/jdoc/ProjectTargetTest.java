package dev.jdoc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectTargetTest {
  @TempDir Path work;

  @Test
  void mavenReleaseInParentPropertiesWinsOverInstalledJdk() throws IOException {
    Files.writeString(work.resolve("pom.xml"), "<project><properties>"
        + "<java.version>17</java.version><maven.compiler.release>${java.version}</maven.compiler.release>"
        + "</properties></project>");
    Path child = Files.createDirectory(work.resolve("service"));
    Files.writeString(child.resolve("pom.xml"), "<project><parent><relativePath>../pom.xml"
        + "</relativePath></parent></project>");
    Path nested = Files.createDirectory(child.resolve("src"));
    ProjectTarget target = ProjectTarget.detect(nested);
    assertEquals(17, target.major);
    assertTrue(target.apiRestricted);
    assertTrue(target.source.contains("maven.compiler.release"));
  }

  @Test
  void compilerPluginReleaseBeatsToolchainAndWeakTarget() throws IOException {
    Files.writeString(work.resolve("pom.xml"), "<project><properties>"
        + "<maven.compiler.target>25</maven.compiler.target></properties><build><plugins>"
        + "<plugin><artifactId>maven-compiler-plugin</artifactId><configuration>"
        + "<release>17</release><jdkToolchain><version>21</version></jdkToolchain>"
        + "</configuration></plugin></plugins></build></project>");
    assertEquals(17, ProjectTarget.detect(work).major);
    assertTrue(ProjectTarget.detect(work).apiRestricted);
  }

  @Test
  void gradleReleaseWinsOverToolchainAndWeakCompatibility() throws IOException {
    Files.writeString(work.resolve("build.gradle.kts"), "// options.release.set(25)\n"
        + "java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }\n"
        + "tasks.withType<JavaCompile> { options.release.set(17) }\n"
        + "targetCompatibility = JavaVersion.VERSION_25\n");
    ProjectTarget target = ProjectTarget.detect(work);
    assertEquals(17, target.major);
    assertTrue(target.apiRestricted);
    assertTrue(target.source.contains("options.release"));
  }

  @Test
  void toolchainAndTargetCompatibilityAreNotApiGuarantees() throws IOException {
    Files.writeString(work.resolve("build.gradle"), "java { toolchain {"
        + "languageVersion = JavaLanguageVersion.of(21) } }\n"
        + "targetCompatibility = JavaVersion.VERSION_17\n");
    assertEquals(21, ProjectTarget.detect(work).major);
    assertFalse(ProjectTarget.detect(work).apiRestricted);
    Files.writeString(work.resolve("build.gradle"), "targetCompatibility = '17'\n");
    assertEquals(17, ProjectTarget.detect(work).major);
    assertFalse(ProjectTarget.detect(work).apiRestricted);
    Files.writeString(work.resolve("build.gradle"), "options.release.set(provider.get())\n");
    assertNull(ProjectTarget.detect(work));
  }

  @Test
  void commonMavenJavaVersionConventionIsLabeledAsWeakEvidence() throws IOException {
    Files.writeString(work.resolve("pom.xml"),
        "<project><properties><java.version>17</java.version></properties></project>");
    ProjectTarget target = ProjectTarget.detect(work);
    assertEquals(17, target.major);
    assertFalse(target.apiRestricted);
    assertTrue(target.source.contains("java.version convention"));
  }
}

package io.atworks.apiintelligence.domain.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import org.junit.jupiter.api.Test;

class SourceDomainTest {

    @Test
    void validatesLocation() {
        assertThat(new SourceLocation("./src\\A.java", 1, 1, 2, 1).relativePath()).isEqualTo(
            "src/A.java");
        assertThatThrownBy(() -> new SourceLocation("../secret", 1, 1, 1, 1)).isInstanceOf(
            IllegalArgumentException.class);
        assertThatThrownBy(() -> new SourceLocation("A", 2, 1, 1, 1)).isInstanceOf(
            IllegalArgumentException.class);
    }

    @Test
    void validatesGitSource() {
        assertThatCode(() -> new GitSource(URI.create("https://host/repo.git"), null,
            null)).doesNotThrowAnyException();
        assertThatThrownBy(
            () -> new GitSource(URI.create("ssh://host/repo"), null, null)).isInstanceOf(
            IllegalArgumentException.class);
        assertThatThrownBy(() -> new GitSource(URI.create("https://host/repo"), RevisionType.BRANCH,
            null)).isInstanceOf(IllegalArgumentException.class);
    }
}

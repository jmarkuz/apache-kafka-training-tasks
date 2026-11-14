package edu.jmarkuz.training.handler;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WikimediaChangesHandlerKeyTest {

    @Test
    void usesServerNameAsKey() {
        assertThat(WikimediaChangesHandler.extractKey("{\"server_name\":\"en.wikipedia.org\",\"bot\":false}"))
                .isEqualTo("en.wikipedia.org");
    }

    @Test
    void returnsNullWhenServerNameIsMissingOrNull() {
        assertThat(WikimediaChangesHandler.extractKey("{\"bot\":false}")).isNull();
        assertThat(WikimediaChangesHandler.extractKey("{\"server_name\":null}")).isNull();
    }

    @Test
    void returnsNullForInvalidJson() {
        assertThat(WikimediaChangesHandler.extractKey("not json")).isNull();
    }
}

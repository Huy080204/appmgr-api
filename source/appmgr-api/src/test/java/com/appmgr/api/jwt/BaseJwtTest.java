package com.appmgr.api.jwt;

import com.appmgr.api.utils.ZipUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BaseJwtTest {

    @Test
    void shouldDecodeTenantIdWhenAdditionalInfoHasEleventhPart() {
        String encoded = ZipUtils.zipString("42|-1|1|<>|-1|1|admin|-1|-1|true|tenant-9");

        BaseJwt result = BaseJwt.decode(encoded);

        assertThat(result.getAccountId()).isEqualTo(42L);
        assertThat(result.getUserKind()).isEqualTo(1);
        assertThat(result.getUsername()).isEqualTo("admin");
        assertThat(result.getIsSuperAdmin()).isTrue();
        assertThat(result.getTenantId()).isEqualTo("tenant-9");
    }

    @Test
    void shouldLeaveTenantIdEmptyWhenAdditionalInfoHasNoTenantPart() {
        String encoded = ZipUtils.zipString("42|-1|1|<>|-1|1|admin|-1|-1|false");

        BaseJwt result = BaseJwt.decode(encoded);

        assertThat(result.getAccountId()).isEqualTo(42L);
        assertThat(result.getIsSuperAdmin()).isFalse();
        assertThat(result.getTenantId()).isEqualTo(BaseJwt.EMPTY_STRING);
    }

    @Test
    void shouldReadEmptyTenantMarkerAsNullWhenTenantPartIsPlaceholder() {
        String encoded = ZipUtils.zipString("42|-1|1|<>|-1|1|admin|-1|-1|true|<>");

        BaseJwt result = BaseJwt.decode(encoded);

        assertThat(result.getTenantId()).isNull();
    }

    @Test
    void shouldReturnNullWhenAdditionalInfoHasTooFewParts() {
        String encoded = ZipUtils.zipString("42|-1|1");

        assertThat(BaseJwt.decode(encoded)).isNull();
    }
}

package com.myfinaimanager.core.financialinstrument.business.normalization;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The plain mapping-data carriers ({@link ExchangeRule}, {@link SuffixOverride},
 * {@link ExchangeMicTable}, {@link SuffixOverrideTable}) — their null/blank-tolerant predicates and
 * case-insensitive lookups, which {@link YahooSymbolNormalizer} relies on.
 */
class NormalizationDataTest {

    @Test
    void exchange_rule_predicates_tolerate_null_and_blank() {
        ExchangeRule empty = new ExchangeRule("X", null, "  ", null, false);
        assertThat(empty.hasSuffix()).isFalse();
        assertThat(empty.hasMic()).isFalse();
        assertThat(empty.hasCurrency()).isFalse();

        ExchangeRule full = new ExchangeRule("X", "MC", "XMAD", "EUR", true);
        assertThat(full.hasSuffix()).isTrue();
        assertThat(full.hasMic()).isTrue();
        assertThat(full.hasCurrency()).isTrue();
    }

    @Test
    void suffix_override_currency_predicate_tolerates_null_and_blank() {
        assertThat(new SuffixOverride("XEUR", null).hasCurrency()).isFalse();
        assertThat(new SuffixOverride("XEUR", "  ").hasCurrency()).isFalse();
        assertThat(new SuffixOverride("XPAR", "EUR").hasCurrency()).isTrue();
    }

    @Test
    void exchange_mic_table_lookup_is_case_insensitive_and_null_safe() {
        ExchangeMicTable t = new ExchangeMicTable(Map.of(
                "MCE", new ExchangeRule("MCE", "MC", "XMAD", "EUR", true)));
        assertThat(t.ruleFor(" mce ")).isPresent();
        assertThat(t.ruleFor("ZZZ")).isEmpty();
        assertThat(t.ruleFor(null)).isEmpty();
    }

    @Test
    void suffix_override_table_lookup_requires_a_suffix_and_is_case_insensitive() {
        SuffixOverrideTable t = new SuffixOverrideTable(Map.of(
                SuffixOverrideTable.key("FRA", "DE"), new SuffixOverride("XETR", "EUR")));
        assertThat(t.overrideFor("fra", "de")).isPresent();
        assertThat(t.overrideFor("FRA", null)).isEmpty();
        assertThat(t.overrideFor("FRA", "  ")).isEmpty();
        assertThat(t.overrideFor("FRA", "XX")).isEmpty();
        assertThat(SuffixOverrideTable.key(null, null)).isEqualTo("|");
    }
}

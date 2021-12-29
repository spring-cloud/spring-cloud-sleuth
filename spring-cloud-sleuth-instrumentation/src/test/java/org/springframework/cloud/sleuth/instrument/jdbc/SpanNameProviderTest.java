/*
 * Copyright 2013-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth.instrument.jdbc;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SpanNameProviderTest {

	private static final String DEFAULT_SPAN_NAME = "query";
	private static final String SPAN_NAME_FOR_SELECTS = "select";
	private static final String SPAN_NAME_FOR_UPDATES = "update";
	private static final String SPAN_NAME_FOR_INSERTS = "insert";
	private static final String SPAN_NAME_FOR_DELETES = "delete";

	@Test
	public void should_return_default_on_null_input() {
		SpanNameProvider provider = new SpanNameProvider();
		String sql = null;

		@SuppressWarnings("ConstantConditions")
		String result = provider.getSpanNameFor(sql);

		assertThat(result).isEqualTo(DEFAULT_SPAN_NAME);
	}

	@Test
	public void should_return_default_on_empty_input() {
		SpanNameProvider provider = new SpanNameProvider();
		String sql = "";

		String result = provider.getSpanNameFor(sql);

		assertThat(result).isEqualTo(DEFAULT_SPAN_NAME);
	}

	@Test
	public void should_return_word_select_on_input_starting_with_word_select() {
		SpanNameProvider provider = new SpanNameProvider();
		String sql = "SELECT * FROM test_table;";

		String result = provider.getSpanNameFor(sql);

		assertThat(result).isEqualTo(SPAN_NAME_FOR_SELECTS);
	}

	@Test
	public void should_return_word_update_on_input_starting_with_word_update() {
		SpanNameProvider provider = new SpanNameProvider();
		String sql = "UPDATE test_table SET foo = 'bar';";

		String result = provider.getSpanNameFor(sql);

		assertThat(result).isEqualTo(SPAN_NAME_FOR_UPDATES);
	}

	@Test
	public void should_return_word_insert_on_input_starting_with_word_insert() {
		SpanNameProvider provider = new SpanNameProvider();
		String sql = "INSERT INTO test_table (foo) VALUES ('bar');";

		String result = provider.getSpanNameFor(sql);

		assertThat(result).isEqualTo(SPAN_NAME_FOR_INSERTS);
	}

	@Test
	public void should_return_word_delete_on_input_starting_with_word_delete() {
		SpanNameProvider provider = new SpanNameProvider();
		String sql = "DELETE FROM test_table;";

		String result = provider.getSpanNameFor(sql);

		assertThat(result).isEqualTo(SPAN_NAME_FOR_DELETES);
	}

	@Test
	public void should_be_case_insensitive() {
		SpanNameProvider provider = new SpanNameProvider();
		String lowerCaseSql = "select * from test_table;";
		String mixedCaseSql = "SelECT * FRom TeSt_TaBLE;";
		String upperCaseSql = "SELECT * FROM TEST_TABLE;";

		String resultForLowerCaseInput = provider.getSpanNameFor(lowerCaseSql);
		String resultForMixedCaseInput = provider.getSpanNameFor(mixedCaseSql);
		String resultForUpperCaseInput = provider.getSpanNameFor(upperCaseSql);

		assertThat(resultForLowerCaseInput).isEqualTo(SPAN_NAME_FOR_SELECTS);
		assertThat(resultForMixedCaseInput).isEqualTo(SPAN_NAME_FOR_SELECTS);
		assertThat(resultForUpperCaseInput).isEqualTo(SPAN_NAME_FOR_SELECTS);
	}

	@Test
	public void should_handle_sql_without_redundant_spaces() {
		SpanNameProvider provider = new SpanNameProvider();
		String sql = "select*from test_table;";

		String result = provider.getSpanNameFor(sql);

		assertThat(result).isEqualTo(SPAN_NAME_FOR_SELECTS);
	}

	@Test
	public void should_return_default_for_input_starting_with_not_handled_word() {
		SpanNameProvider provider = new SpanNameProvider();
		String sql = "WITH a AS SELECT * FROM test_table SELECT * FROM a;";

		String result = provider.getSpanNameFor(sql);

		assertThat(result).isEqualTo(DEFAULT_SPAN_NAME);
	}
}

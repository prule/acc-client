package com.github.prule.acc.client

import io.kaitai.struct.KaitaiStream
import io.kaitai.struct.KaitaiStruct
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class JsonFormatterTest {

  data class SimplePojo(val id: Int, val name: String)

  class DummyKaitaiStruct(
      val data: String,
      val _io: KaitaiStream? = null,
      val _parent: KaitaiStruct? = null,
      val _root: KaitaiStruct? = null,
  ) : KaitaiStruct(_io)

  @Test
  fun `should serialize simple pojo to json`() {
    val obj = SimplePojo(1, "Test")
    val json = JsonFormatter.toJsonString(obj)

    assertThat(json).contains("\"id\":1")
    assertThat(json).contains("\"name\":\"Test\"")
  }

  @Test
  fun `should filter out internal kaitai fields`() {
    val struct = DummyKaitaiStruct("payload")
    val json = JsonFormatter.toJsonString(struct)

    assertThat(json).contains("\"data\":\"payload\"")
    assertThat(json).doesNotContain("_io")
    assertThat(json).doesNotContain("_parent")
    assertThat(json).doesNotContain("_root")
  }
}

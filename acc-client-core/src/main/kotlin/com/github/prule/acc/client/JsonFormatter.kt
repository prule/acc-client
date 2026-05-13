package com.github.prule.acc.client

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonFilter
import com.fasterxml.jackson.annotation.PropertyAccessor
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider
import io.kaitai.struct.KaitaiStruct

object JsonFormatter {
  @JsonFilter("kaitaiFilter") class KaitaiMixin

  val filter: SimpleBeanPropertyFilter? =
    SimpleBeanPropertyFilter.serializeAllExcept("_parent", "_root", "_io")

  val mapper =
    ObjectMapper().apply {
      configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
      setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
      addMixIn(KaitaiStruct::class.java, KaitaiMixin::class.java)
      setFilterProvider(SimpleFilterProvider().addFilter("kaitaiFilter", filter))
    }

  fun toJsonString(obj: Any): String = mapper.writeValueAsString(obj)
}

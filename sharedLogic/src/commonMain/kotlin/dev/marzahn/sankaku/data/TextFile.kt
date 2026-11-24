package dev.marzahn.sankaku.data

interface TextFile {
  fun read(): String?

  fun write(text: String)

  fun delete()
}

class InMemoryTextFile(private var text: String? = null) : TextFile {
  override fun read(): String? = text

  override fun write(text: String) {
    this.text = text
  }

  override fun delete() {
    text = null
  }
}

package labs.newrapaw.dlna.probe

fun buildPlayPageContent(): String = """
    <section class="page-section" id="play">
      <div class="section-head">
        <h3>播放控制</h3>
      </div>
      <form class="stack-form" method="post" action="/control/play" data-control-form>
        <label for="url">输入 m3u8 地址</label>
        <textarea id="url" name="url" autofocus></textarea>
        <button type="submit">开始播放</button>
      </form>
      <form class="inline-form" method="post" action="/control/stop" data-control-form>
        <button type="submit">停止播放</button>
      </form>
    </section>
""".trimIndent()

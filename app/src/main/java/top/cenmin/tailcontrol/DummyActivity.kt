package top.cenmin.tailcontrol

import android.app.Activity
import android.os.Bundle

class DummyActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish() // 立即关闭，不显示任何界面
    }
}

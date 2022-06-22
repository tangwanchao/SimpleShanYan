package me.twc.shanyan

import android.content.Context
import android.util.Log
import com.chuanglan.shanyan_sdk.OneKeyLoginManager
import com.chuanglan.shanyan_sdk.tool.ShanYanUIConfig
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

/**
 * @author 唐万超
 * @date 2022/06/22
 *
 * 参考链接: http://shanyan.253.com/document/details?lid=519&cid=93&pc=28&pn=%25E9%2597%25AA%25E9%25AA%258CSDK#b57c3e07
 */
object ShanYanManager {

    // 状态码 : 初始化成功
    private const val CODE_INIT_SUCCESS = 1022

    // 状态码 : 预取号成功
    private const val CODE_PREFETCH_NUMBER_SUCCESS = 1022

    // 状态码 : 授权页拉起成功
    private const val CODE_OPEN_LOGIN_AUTH_SUCCESS = 1000

    // 状态码 : 登录成功
    private const val CODE_LOGIN_SUCCESS = 1000

    // 状态码 : 点击返回键
    private const val CODE_LOGIN_CLICK_BACK = 1011

    private val mInitialized = AtomicBoolean(false)

    // SDK 是否初始化状态
    private var mInitializedState: Boolean = false

    // 预取号
    private var mPrefetchNumber = PrefetchNumber()

    var mAppId = ""

    /**
     * 初始化闪验 SDK
     *
     * @param callback 初始化结果
     */
    fun initialize(context: Context, appId: String, callback: ((success: Boolean) -> Unit)? = null) {
        mAppId = appId
        if (mInitialized.compareAndSet(false, true)) {
            OneKeyLoginManager.getInstance().init(context, appId) { code, result ->
                val success = code == CODE_INIT_SUCCESS
                mInitializedState = success
                if (!success) mInitialized.set(false)
                Log.d("SimpleShanYan", "闪验 SDK 初始化结果($mInitializedState) code = $code, result = $result")
                callback?.invoke(success)
            }
        }
    }

    /**
     * 预取号
     *
     * 如果 SDK 初始化失败,尝试重新初始化后预取号
     *
     * @param callback 预取号结果
     */
    fun prefetchNumber(
        context: Context,
        callback: ((prefetchNumber: PrefetchNumber) -> Unit)? = null
    ) {
        val prefetch = fun() {
            if (mPrefetchNumber.valid()) {
                callback?.invoke(mPrefetchNumber)
                Log.d("SimpleShanYan", "使用缓存预取号结果: $mPrefetchNumber")
                return
            }
            val time = System.currentTimeMillis()
            OneKeyLoginManager.getInstance().getPhoneInfo { code, result ->
                mPrefetchNumber = PrefetchNumber(code == CODE_PREFETCH_NUMBER_SUCCESS, time)
                Log.d("SimpleShanYan", "预取号结果:code = $code,result = $result")
                callback?.invoke(mPrefetchNumber)
            }
        }

        if (mInitializedState) {
            prefetch()
        } else {
            initialize(context, mAppId) { prefetch() }
        }
    }

    /**
     * 闪验登录
     *
     * 注意:
     * 如果预取号过期,那么闪验将先进行预取号操作,这将导致授权页出现有延迟(1-3秒 左右)
     *
     * @param portrait 竖屏 UI 配置
     * @param land 横屏 UI 配置
     * @param autoFinish 授权页是否自动销毁
     * autoFinish = true
     * 1.在授权登录页面，当用户主动点击左左上角返回按钮时，返回码为1011，SDK将自动销毁授权页；
     * 2.安卓 SDK，当用户点击手机的硬件返回键（相当于取消登录），返回码为1011，SDK将自动销毁授权页
     * 3.当用户设置一键登录或者其他自定义控件为自动销毁时，得到回调后，授权页面会自动销毁
     * autoFinish = false
     * 1.当设置一键登录为手动销毁时，点击授权页一键登录按钮成功获取token不会自动销毁授权页，请务必在回调中处理完自己的逻辑后手动调用销毁授权页方法。
     * 2.当设置自定义控件为手动销毁时，请务必在回调中处理完自己的逻辑后手动调用销毁授权页方法。
     * @see closeLoginAuth
     *
     * @param listener 授权相关回调,参考 [FlashLoginState]
     */
    fun flashLogin(
        context: Context,
        portrait: ShanYanUIConfig? = null,
        land: ShanYanUIConfig? = null,
        autoFinish: Boolean = false,
        listener: FlashLoginListener
    ) {
        // 预取号无效首先进行预取号
        if (!mPrefetchNumber.valid()) {
            listener.callback(FlashLoginState.START_PREFETCH_NUMBER, "开始预取号,可能有 3 秒左右耗时")
            prefetchNumber(context) { pn ->
                if (pn.valid()) {
                    listener.callback(FlashLoginState.PREFETCH_NUMBER_SUCCESS, "预取号成功")
                    flashLogin(context, portrait, land, autoFinish, listener)
                } else {
                    listener.callback(FlashLoginState.PREFETCH_NUMBER_FAILURE, "预取号失败")
                }
            }
        }
        // 配置 UI
        OneKeyLoginManager.getInstance().setAuthThemeConfig(portrait, land)
        // 拉起授权页回调
        val openLoginAuthCallback = fun(code: Int, result: String) {
            Log.d("SimpleShanYan", "拉起授权页结果:code = $code,result = $result")
            if (code == CODE_OPEN_LOGIN_AUTH_SUCCESS) {
                listener.callback(FlashLoginState.SUCCESS, result)
            } else {
                listener.callback(FlashLoginState.FAILURE, result)
            }
        }

        // 登录结果回调
        val loginCallback = fun(code: Int, result: String) {
            Log.d("SimpleShanYan", "登录结果:code = $code,result = $result")
            when (code) {
                CODE_LOGIN_SUCCESS -> {
                    var token = ""
                    try {
                        val jsonObject = JSONObject(result)
                        token = jsonObject.getString("token")
                    } catch (th: Throwable) {
                        th.printStackTrace()
                    }
                    if (token.isNotBlank()) {
                        listener.callback(FlashLoginState.LOGIN_SUCCESS, token)
                    } else {
                        listener.callback(FlashLoginState.LOGIN_FAILURE, result)
                    }
                }
                CODE_LOGIN_CLICK_BACK -> {
                    listener.callback(FlashLoginState.LOGIN_FAILURE_BACK, result)
                }
                else -> {
                    listener.callback(FlashLoginState.LOGIN_FAILURE, result)
                }
            }
        }

        OneKeyLoginManager.getInstance().openLoginAuth(
            autoFinish, openLoginAuthCallback, loginCallback
        )
    }

    /**
     * 设置授权页 loading 框是否可见
     *
     * @param visible [true:visible],[false:invisible]
     */
    fun setAuthLoadingVisibility(visible: Boolean) {
        OneKeyLoginManager.getInstance().setLoadingVisibility(visible)
    }

    /**
     * 移除所有监听后关闭授权页
     */
    fun closeLoginAuth() {
        OneKeyLoginManager.getInstance().removeAllListener()
        OneKeyLoginManager.getInstance().finishAuthActivity()
    }

    data class PrefetchNumber(
        // 预取号是否成功
        val state: Boolean = false,
        // 预取号调用时间
        val time: Long = 0L
    ) {
        /**
         * 该预取号是否有效
         *
         * 移动——48小时
         * 联通——1小时
         * 电信——1小时
         *
         * 参考链接:
         * http://shanyan.253.com/document/details?lid=475&cid=91&pc=28&pn=%25E9%2597%25AA%25E9%25AA%258CSDK
         *
         * @return [true : 该预取号有效]
         *         [false: 预取号无效]
         */
        fun valid(): Boolean {
            return state && System.currentTimeMillis() - time < 30 * 60 * 1000L
        }
    }

    interface FlashLoginListener {
        fun callback(state: FlashLoginState, result: String)
    }

    /**
     * 闪验登录状态
     */
    @Suppress("unused")
    enum class FlashLoginState {
        /**
         * 开始预取号-仅仅在预取号无效时有该回调
         *
         * 该状态之后一定有
         * PREFETCH_NUMBER_SUCCESS,
         * PREFETCH_NUMBER_FAILURE
         * 之一的状态回调
         *
         * @see PREFETCH_NUMBER_SUCCESS
         * @see PREFETCH_NUMBER_FAILURE
         */
        START_PREFETCH_NUMBER,

        /**
         * 预取号成功-仅仅在 START_PREFETCH_NUMBER 后回调
         *
         * @see START_PREFETCH_NUMBER
         */
        PREFETCH_NUMBER_SUCCESS,

        /**
         * 预取号失败-仅仅在 START_PREFETCH_NUMBER 后回调
         *
         * @see START_PREFETCH_NUMBER
         */
        PREFETCH_NUMBER_FAILURE,

        /**
         * 拉起授权页成功
         */
        SUCCESS,

        /**
         * 拉起授权页失败
         */
        FAILURE,

        /**
         * 登录成功
         */
        LOGIN_SUCCESS,

        /**
         * 登录失败-返回
         */
        LOGIN_FAILURE_BACK,

        /**
         * 登录失败
         */
        LOGIN_FAILURE
    }
}
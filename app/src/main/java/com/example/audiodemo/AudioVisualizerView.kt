package com.example.audiodemo

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.media.MediaPlayer
import android.media.MediaPlayer.OnPreparedListener
import android.media.audiofx.Visualizer
import android.util.AttributeSet
import android.util.Log
import android.view.View
import kotlin.math.log10

/**
 * Created by xuhao on 2021/3/15.
 * 自定义view，用于实现音频可视化动效
 */
class AudioVisualizerView @JvmOverloads constructor(context: Context,
                                                    attrs: AttributeSet? = null,
                                                    defStyleAttr: Int = 0) : View(context, attrs, defStyleAttr) {
    var visualizer: Visualizer? = null
    private var mSamplingRate = Visualizer.getCaptureSizeRange()[0]
    private var mModel = FloatArray(mSamplingRate / 2)
    private var mModelAfterSmoothing: FloatArray? = null

    private var mPoints: FloatArray? = null
    private val mPaint = Paint()

    private val mRect = RectF()

    private var mColor = Color.RED

    private var mediaPlayer: MediaPlayer? = null
    private var audioSessionId: Int = -9999
    private var itemMargin = 5f

    init {
        mPaint.color = mColor
        mPaint.isAntiAlias = true
    }

    companion object {
        const val SESSION_ID_ERROR_CODE = -9999
        const val TAG = "AudioVisualizerView"
        const val SMOOTHING_WINDOW_SIZE_SMALL = 5
        const val SMOOTHING_WINDOW_SIZE_MEDIAN = 7
        const val SMOOTHING_WINDOW_SIZE_LARGE = 9
    }

    private fun initVisualizer(audioSessionId: Int) {
        if (audioSessionId != SESSION_ID_ERROR_CODE) {      //如果传入audioSessionId，那么就通过Visualizer的方式来实现音频可视化
            try {
                this.audioSessionId = audioSessionId
                visualizer = Visualizer(audioSessionId)
            } catch (e: Exception) {
                Log.e(TAG, "Visualizer initialization failed", e)
            }
            visualizer?.captureSize = mSamplingRate
            visualizer?.setDataCaptureListener(mOnDataCaptureListener, Visualizer.getMaxCaptureRate() / 4, false, true)
            //需要在使用前开启
            visualizer?.enabled = true
        } else {                                            //否则通过手动拉取音频pcm编码数据，然后做FFT来实现

        }
    }

    private val mOnDataCaptureListener by lazy {
        object : Visualizer.OnDataCaptureListener {
            //频域信息（这里采用频域数据来实现可视化）
            override fun onFftDataCapture(visualizer: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                visualizer ?: return
                fft ?: return
                convertFFTData(fft)
                invalidate()
            }

            //时域信息（这里不关注）
            override fun onWaveFormDataCapture(visualizer: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
                //ignore
            }
        }
    }

    private fun convertFFTData(fft: ByteArray) {
        if (fft.size != mSamplingRate) {
            return
        }
        var hypotResult = Math.abs(fft[1].toInt()).toFloat()
        mModel[0] = if (hypotResult < 0) 127f else hypotResult
        var i = 2
        while (i < mSamplingRate) {
            hypotResult = Math.hypot(fft[i].toDouble(), fft[i + 1].toDouble()).toFloat()
            mModel[i / 2] = if (hypotResult < 0) 127f else hypotResult
            i += 2
        }

        //为了视觉效果，对数据做平滑处理
        dataSmoothing(SMOOTHING_WINDOW_SIZE_LARGE)
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        if (mModelAfterSmoothing == null || mModelAfterSmoothing!!.isEmpty()) {
            return
        }
        val mSpectrumShowCount = mModelAfterSmoothing?.size ?: 0
        if (mPoints == null ||mSpectrumShowCount == 0 || mPoints?.size != mSpectrumShowCount * 4) {
            mPoints = FloatArray(mSpectrumShowCount * 4)
        }
        mRect.set(0f, 0f, width.toFloat(), height.toFloat())
        val singleItemWidth = (width - itemMargin * (mSpectrumShowCount - 1)) / mSpectrumShowCount
        mPaint.strokeWidth = singleItemWidth
        for (i in 0 until mSpectrumShowCount) {
            mPoints!![i * 4] = (singleItemWidth + itemMargin) * i + singleItemWidth / 2
            mPoints!![i * 4 + 1] = mRect.bottom
            mPoints!![i * 4 + 2] = (singleItemWidth + itemMargin) * i + singleItemWidth / 2
            mPoints!![i * 4 + 3] = mRect.bottom - 20 * log10(mModelAfterSmoothing!![i]) - 10       //这里减10是为了不至于当谱峰为0时导致空出一块
        }
        canvas?.drawLines(mPoints!!, mPaint)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        release()
    }

    /**
     * 退出前必须调用，不然会抛异常
     */
    fun release() {
        visualizer?.apply {
            enabled = false
            release()
        }
        visualizer = null
    }

    /**
     * 设置采样率，采样率越高越精确，但是也越耗时
     * 注意：采样率必须为2的指数倍
     */
    fun setSamplingRate(samplingRate: Int) {
        if (samplingRate % 2 == 0 && samplingRate > 0) {
            if (mSamplingRate != samplingRate) {
                mModel = FloatArray(samplingRate / 2)
                mSamplingRate = samplingRate
            }
        } else {
            throw IllegalArgumentException("The sampling rate should be an exponential of 2")
        }
    }

    fun setColor(colorRes: Int) {
        try {
            mPaint.color = Color.parseColor(colorRes.toString())
            mColor = mPaint.color
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "颜色解析错误")
        }
    }

    fun doPlayByMediaPlayer(raw: Int) {
        try {
            if (mediaPlayer == null) {
                mediaPlayer = MediaPlayer.create(context, raw)
            }
            if (mediaPlayer == null) {
                Log.e(TAG, "mediaPlayer is null")
                return
            }
            mediaPlayer?.setOnErrorListener(null)
            mediaPlayer?.setOnPreparedListener(OnPreparedListener {
                initVisualizer(mediaPlayer?.audioSessionId ?: SESSION_ID_ERROR_CODE)
            })
            mediaPlayer?.start()
        } catch (e: Exception) {
            Log.e(TAG, e.message.toString())
        }
    }

    //为了视觉效果，对数据进行平滑处理
    //暂时只支持窗口大小为5、7、9
    private fun dataSmoothing(windowSize: Int) {
        val offset = windowSize / 2
        if (mModel.isEmpty() || windowSize <= 1 || mModel.size <= offset + 1) {
            return
        }
        when (windowSize) {
            SMOOTHING_WINDOW_SIZE_SMALL -> {
                if (mModelAfterSmoothing == null) {
                    mModelAfterSmoothing = FloatArray(mModel.size - windowSize + 1)
                }
                for (i in offset until (mModel.size - offset)) {
                    mModelAfterSmoothing!![i - offset] = (-3 * mModel[i - offset]
                            + 12 * mModel[i - 1]
                            + 17 * mModel[i]
                            + 12 * mModel[i + 1]
                            - 3 * mModel[i + offset]) / 35
                }
            }
            SMOOTHING_WINDOW_SIZE_MEDIAN -> {
                if (mModelAfterSmoothing == null) {
                    mModelAfterSmoothing = FloatArray(mModel.size - windowSize + 1)
                }
                for (i in offset until (mModel.size - offset)) {
                    mModelAfterSmoothing!![i - offset] = (-2 * mModel[i - offset]
                            + 3 * mModel[i - offset + 1]
                            + 6 * mModel[i - 1]
                            + 7 * mModel[i]
                            + 6 * mModel[i + 1]
                            + 3 * mModel[i + offset - 1]
                            - 2 * mModel[i + offset]) / 21
                }
            }
            SMOOTHING_WINDOW_SIZE_LARGE -> {
                if (mModelAfterSmoothing == null) {
                    mModelAfterSmoothing = FloatArray(mModel.size - windowSize + 1)
                }
                for (i in offset until (mModel.size - offset)) {
                    mModelAfterSmoothing!![i - offset] = (-21 * mModel[i - offset]
                            + 14 * mModel[i - offset + 1]
                            + 39 * mModel[i - offset + 2]
                            + 54 * mModel[i - 1]
                            + 59 * mModel[i]
                            + 54 * mModel[i + 1]
                            + 39 * mModel[i + offset - 2]
                            + 14 * mModel[i + offset - 1]
                            - 21 * mModel[i + offset]) / 231
                }
            }
            else -> {
                if (mModelAfterSmoothing == null) {
                    mModelAfterSmoothing = FloatArray(mModel.size)
                }
                for (i in mModel.indices) {
                    mModelAfterSmoothing!![i] = mModel[i]
                }
            }
        }
    }

    private val mAudioTrackPlayer: AudioTrackPlayer by lazy { AudioTrackPlayer() }

    private var isAudioTrackStart = false

    fun doPlayByAudioTrackPlayer(path: String) {
        mAudioTrackPlayer.createAudioTrack()
        mAudioTrackPlayer.openPCMFile(context, path)
        if (!isAudioTrackStart) {
            mAudioTrackPlayer.mPlayAudioThread.start()
            isAudioTrackStart = true
        }
    }

    /**
     * FFT变换
     * 另附一个链接：https://blog.csdn.net/Flag_z/article/details/99163939?utm_medium=distribute.pc_relevant.none-task-blog-BlogCommendFromMachineLearnPai2-1.control&dist_request_id=&depth_1-utm_source=distribute.pc_relevant.none-task-blog-BlogCommendFromMachineLearnPai2-1.control
     */
//    void fft(cp *a,int n,int inv)
//    {
//        int bit=0;
//        while ((1<<bit)<n)bit++;
//        fo(i,0,n-1)
//        {
//            rev[i]=(rev[i>>1]>>1)|((i&1)<<(bit-1));
//            if (i<rev[i])swap(a[i],a[rev[i]]);//不加这条if会交换两次（就是没交换）
//        }
//        for (int mid=1;mid<n;mid*=2)//mid是准备合并序列的长度的二分之一
//        {
//            cp temp(cos(pi/mid),inv*sin(pi/mid));//单位根，pi的系数2已经约掉了
//            for (int i=0;i<n;i+=mid*2)//mid*2是准备合并序列的长度，i是合并到了哪一位
//            {
//                cp omega(1,0);
//                for (int j=0;j<mid;j++,omega*=temp)//只扫左半部分，得到右半部分的答案
//                {
//                    cp x=a[i+j],y=omega*a[i+j+mid];
//                    a[i+j]=x+y,a[i+j+mid]=x-y;//这个就是蝴蝶变换什么的
//                }
//            }
//        }
//    }

}
package com.example.pillrecognitionapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview as CameraPreview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.ui.tooling.preview.Preview as ComposePreview
import androidx.exifinterface.media.ExifInterface
import com.example.pillrecognitionapp.ui.home.HomeScreen
import com.example.pillrecognitionapp.ui.theme.PillRecognitionAppTheme
import java.io.File
import java.io.InputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.concurrent.TimeUnit
import java.util.Locale

// ===== Retrofit / 네트워크 관련 =====
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.GET
import retrofit2.http.Query

// ===== ViewModel 관련 =====
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

// ===== ML Kit: Text Recognition (OCR) =====
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

// ==========================
//  API 응답 모델 (DTO)
// ==========================
data class PillRecognitionResponse(
    val id: String,
    val name: String,
    val company: String?,
    val ingredient: String?,
    val appearance: String?,
    val shape: String?,
    val effect: String?,
    val caution_before_taking: String?,
    val caution_nomal: String?,
    val interaction: String?,
    val side_effect: String?,
    val updated_at: String? // 날짜는 파싱 오류 방지를 위해 String으로 받습니다.
)

// ==========================
//  결과 화면용 도메인 모델
//  (카메라/검색 둘 다 이걸 사용)
// ==========================
data class PillDetail(
    val id: String,
    val company: String,
    val name: String,
    val efficacy: String,
    val dosage: String,
    val ingredients: String,
    val sideEffects: String,
    val contraindications: String,
    val interactions: String
)

// 서버에서 받은 진짜 식약처 데이터를 화면(PillDetail)에 꽂아 넣습니다!
fun PillRecognitionResponse.toPillDetail(recognizedText: String? = null): PillDetail {
    val displayName = name.ifBlank { recognizedText ?: "이름 정보 없음" }

    return PillDetail(
        id = id,
        company = company ?: "제조사 정보 없음",
        name = displayName,
        efficacy = effect ?: "효능/효과 정보가 없습니다.",
        dosage = caution_before_taking ?: "용법/용량 정보가 없습니다.",
        ingredients = ingredient ?: "성분 정보가 없습니다.",
        sideEffects = side_effect ?: "부작용 정보가 없습니다.",
        contraindications = caution_nomal ?: "일반 주의사항 정보가 없습니다.",
        interactions = interaction ?: "상호작용 정보가 없습니다."
    )
}

// ==========================
//  Retrofit API 정의
// ==========================
interface PillApiService {

    @Multipart
    @POST("/api/v1/drugs/recognize")
    suspend fun recognizePill(
        @Part file: MultipartBody.Part,
        @Part("recognized_text") text: okhttp3.RequestBody? // 👈 OCR 텍스트 보낼 구멍 추가
    ): PillRecognitionResponse

    @Multipart
    @POST("/api/v1/drugs/gpt-image-search")
    suspend fun gptImageSearch(
        @Part file: MultipartBody.Part
    ): PillRecognitionResponse

    @GET("/api/v1/drugs/search")
    suspend fun searchDrugs(
        @Query("keyword") keyword: String
    ): List<PillRecognitionResponse> // 서버의 DrugBase와 앱의 Response 구조가 같으므로 재사용!
}

// ==========================
//  Retrofit 클라이언트
// ==========================
object ApiClient {

    private const val BASE_URL = "http://43.202.242.249/"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val pillApi: PillApiService = retrofit.create(PillApiService::class.java)
}

// ==========================
//  Repository 계층
// ==========================
interface PillRepository {
    suspend fun recognizePill(imageFile: File, text: String?): PillRecognitionResponse
}

class RetrofitPillRepository(
    private val api: PillApiService = ApiClient.pillApi
) : PillRepository {

    override suspend fun recognizePill(imageFile: File, text: String?): PillRecognitionResponse {
        val mimeType = "image/jpeg".toMediaTypeOrNull()
        val requestBody = imageFile.asRequestBody(mimeType)
        val part = MultipartBody.Part.createFormData(
            name = "file",
            filename = imageFile.name,
            body = requestBody
        )

        // 텍스트를 서버가 읽을 수 있는 형태로 포장
        val textBody = text?.toRequestBody("text/plain".toMediaTypeOrNull())

        return api.recognizePill(part, textBody) // 함께 전송!
    }

    suspend fun gptImageSearch(imageFile: File): PillRecognitionResponse {
        val mimeType = "image/jpeg".toMediaTypeOrNull()
        val requestBody = imageFile.asRequestBody(mimeType)
        val part = MultipartBody.Part.createFormData(
            name = "file",
            filename = imageFile.name,
            body = requestBody
        )

        return api.gptImageSearch(part)
    }
}

// ==========================
//  UI 상태 정의
// ==========================
sealed class PillUiState {
    object Idle : PillUiState()
    object Loading : PillUiState()
    data class Success(val result: PillRecognitionResponse) : PillUiState()
    data class Error(val message: String) : PillUiState()
}

// ==========================
//  카메라용 라우트(내부 네비게이션)
// ==========================
sealed class CameraRoute {
    object Camera : CameraRoute()
    data class Preview(val photoPath: String) : CameraRoute()
    data class Result(val detail: PillDetail) : CameraRoute()
}

// ==========================
//  ViewModel
// ==========================
class PillRecognitionViewModel(
    private val repository: PillRepository = RetrofitPillRepository()
) : ViewModel() {

    var uiState by mutableStateOf<PillUiState>(PillUiState.Idle)
        private set

    fun analyze(photoPath: String, recognizedText: String?) { // 파라미터 추가
        val file = File(photoPath)
        if (!file.exists()) {
            uiState = PillUiState.Error("사진 파일을 찾을 수 없습니다.")
            return
        }

        viewModelScope.launch {
            uiState = PillUiState.Loading
            try {
                val result = repository.recognizePill(file, recognizedText) // 텍스트 전달
                uiState = PillUiState.Success(result)
            } catch (e: Exception) {
                Log.e("PillVM", "분석 실패", e)
                uiState = PillUiState.Error("알약 분석 중 오류가 발생했습니다.")
            }
        }
    }

    fun reset() {
        uiState = PillUiState.Idle
    }
}

// ==========================
//  Activity
// ==========================
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 카메라 권한 요청 (미리 받아두기)
        checkCameraPermission()

        enableEdgeToEdge()
        setContent {
            PillRecognitionAppTheme {
                var showCamera by remember { mutableStateOf(false) }
                var showGptSearch by remember { mutableStateOf(false) }
                var gptSearchResult by remember { mutableStateOf<PillDetail?>(null) }

                when {
                    gptSearchResult != null -> {
                        PillResultScreen(
                            detail = gptSearchResult!!,
                            onBack = {
                                gptSearchResult = null
                                showGptSearch = false
                            }
                        )
                    }

                    showGptSearch -> {
                        GptImageSearchScreen(
                            onBack = { showGptSearch = false },
                            onResult = { detail ->
                                gptSearchResult = detail
                            }
                        )
                    }

                    showCamera -> {
                        AppRoot(
                            onExitCamera = { showCamera = false }
                        )
                    }

                    else -> {
                        HomeScreen(
                            onClickSearchCamera = {
                                showGptSearch = true
                            }
                        )
                    }
                }

            }
        }
    }

    // 📌 권한 체크 & 요청
    private fun checkCameraPermission() {
        val permission = Manifest.permission.CAMERA
        val granted = ContextCompat.checkSelfPermission(
            this,
            permission
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(permission),
                CAMERA_PERMISSION_REQUEST_CODE
            )
        }
    }

    companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 100
    }
}

// ==========================
//  카메라 루트(AppRoot) - 카메라 / 미리보기 / 결과 전환
// ==========================
@Composable
fun AppRoot(
    viewModel: PillRecognitionViewModel = viewModel(),
    onExitCamera: () -> Unit = {}
) {
    var currentRoute by remember { mutableStateOf<CameraRoute>(CameraRoute.Camera) }
    val uiState = viewModel.uiState

    // 분석 버튼을 눌렀는지 여부 & OCR 텍스트
    var pendingNavigateToResult by remember { mutableStateOf(false) }
    var recognizedTextForResult by remember { mutableStateOf<String?>(null) }

    // 분석이 끝났을 때 결과 화면으로 이동
    LaunchedEffect(uiState, pendingNavigateToResult) {
        if (pendingNavigateToResult && uiState is PillUiState.Success) {
            val detail = uiState.result.toPillDetail(recognizedTextForResult)
            pendingNavigateToResult = false
            viewModel.reset()
            currentRoute = CameraRoute.Result(detail)
        } else if (pendingNavigateToResult && uiState is PillUiState.Error) {
            // 에러가 나면 pending 플래그만 내려줌 (메시지는 미리보기 화면 상단에 표시)
            pendingNavigateToResult = false
        }
    }

    when (val route = currentRoute) {
        is CameraRoute.Camera -> {
            viewModel.reset()
            CameraScreen(
                onPhotoTaken = { file ->
                    currentRoute = CameraRoute.Preview(file.absolutePath)
                },
                onExitCamera = onExitCamera
            )
        }

        is CameraRoute.Preview -> {
            PhotoPreviewScreen(
                photoPath = route.photoPath,
                isAnalyzing = pendingNavigateToResult && uiState is PillUiState.Loading,
                errorMessage = (uiState as? PillUiState.Error)?.message,
                onAnalyzeAndNavigate = { recognizedText ->
                    recognizedTextForResult = recognizedText
                    pendingNavigateToResult = true
                    viewModel.analyze(route.photoPath, recognizedText) // 여기서 텍스트를 뷰모델로 쏴줌!
                },
                onGoBackToCamera = {
                    viewModel.reset()
                    currentRoute = CameraRoute.Camera
                }
            )
        }

        is CameraRoute.Result -> {
            PillResultScreen(
                detail = route.detail,
                onBack = {
                    // 결과 화면에서 뒤로 → 다시 카메라 미리보기로 가지 않고 홈으로 나가도록 처리
                    onExitCamera()
                }
            )
        }
    }
}

// ==========================
//  카메라 화면
// ==========================
@Composable
fun CameraScreen(
    onPhotoTaken: (File) -> Unit,
    onExitCamera: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding()
    ) {
        // 상단 뒤로 버튼
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.Start
        ) {
            Button(onClick = onExitCamera) {
                Text(text = "뒤로")
            }
        }

        // 카메라 프리뷰
        AndroidView(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                cameraProviderFuture.addListener(
                    {
                        try {
                            val cameraProvider = cameraProviderFuture.get()

                            val preview = CameraPreview.Builder()
                                .build()
                                .apply {
                                    setSurfaceProvider(previewView.surfaceProvider)
                                }

                            val selector = CameraSelector.DEFAULT_BACK_CAMERA

                            val imgCapture = ImageCapture.Builder()
                                .setTargetRotation(previewView.display.rotation)
                                .build()

                            imageCapture = imgCapture

                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                selector,
                                preview,
                                imgCapture
                            )
                        } catch (e: Exception) {
                            Log.e("CameraX", "Use case binding failed", e)
                        }
                    },
                    ContextCompat.getMainExecutor(ctx)
                )

                previewView
            }
        )

        // 촬영 버튼
        Button(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            onClick = {
                val imgCap = imageCapture
                if (imgCap != null) {
                    takePhoto(context, imgCap) { file ->
                        onPhotoTaken(file)
                    }
                } else {
                    Log.w("CameraX", "ImageCapture is null, 카메라 초기화 전입니다.")
                }
            }
        ) {
            Text(text = "촬영", color = Color.White)
        }
    }
}

// ==========================
//  사진 미리보기 화면
//  - 분석하기 버튼 → OCR + 서버 분석 요청
//  - 실제 결과는 별도의 PillResultScreen에서 표시
// ==========================
@Composable
fun PhotoPreviewScreen(
    photoPath: String,
    isAnalyzing: Boolean,
    errorMessage: String?,
    onAnalyzeAndNavigate: (recognizedText: String?) -> Unit,
    onGoBackToCamera: () -> Unit
) {
    val context = LocalContext.current
    var isSaved by remember(photoPath) { mutableStateOf(false) }

    val bitmap = remember(photoPath) {
        loadRotatedBitmap(photoPath)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Captured image preview",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.DarkGray),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "이미지를 불러올 수 없습니다.",
                    color = Color.White
                )
            }
        }

        // 상단 상태/에러 표시
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(Color(0x88000000))
                .padding(8.dp)
        ) {
            when {
                isAnalyzing -> {
                    Spacer(modifier = Modifier.height(30.dp))
                    Text("분석 중입니다...", color = Color.Yellow)
                }
                errorMessage != null -> {
                    Spacer(modifier = Modifier.height(30.dp))
                    Text(errorMessage, color = Color.Red)
                }
                else -> {
                    Spacer(modifier = Modifier.height(30.dp))
                    Text("분석하기 버튼을 눌러 주세요.", color = Color.White)
                }
            }
        }

        // 하단 버튼들
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0xAA000000))
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 재촬영
                Button(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    onClick = {
                        val file = File(photoPath)
                        if (!isSaved) {
                            if (file.exists()) {
                                val deleted = file.delete()
                                if (deleted) {
                                    Log.i("CameraX", "재촬영: 파일 삭제 성공, path = $photoPath")
                                    onGoBackToCamera()
                                } else {
                                    Log.e("CameraX", "재촬영: 파일 삭제 실패, path = $photoPath")
                                    Toast.makeText(
                                        context,
                                        "사진 삭제에 실패했습니다.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            } else {
                                Log.w(
                                    "CameraX",
                                    "재촬영: 삭제하려는 파일이 존재하지 않음, path = $photoPath"
                                )
                                onGoBackToCamera()
                            }
                        } else {
                            onGoBackToCamera()
                        }
                    }
                ) {
                    Text(text = "재촬영", color = Color.White)
                }

                // 저장
                Button(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    onClick = {
                        if (!isSaved) isSaved = true
                        Toast.makeText(context, "사진이 저장되었습니다.", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text(text = "저장", color = Color.White)
                }
            }

            // 분석하기 버튼 (여기서 OCR → 콜백으로 알약 분석 트리거)
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                onClick = {
                    if (bitmap == null) {
                        Toast.makeText(context, "이미지를 불러올 수 없습니다.", Toast.LENGTH_SHORT)
                            .show()
                    } else {
                        recognizeTextFromBitmap(
                            context = context,
                            bitmap = bitmap,
                            onResult = { text ->
                                val cleaned = text.takeIf { it.isNotBlank() }
                                onAnalyzeAndNavigate(cleaned)
                            }
                        )
                    }
                }
            ) {
                Text(text = "분석하기", color = Color.White)
            }
        }
    }
}

// ==========================
//  결과 화면 (카메라/검색 공용 구조와 비슷)
// ==========================
@Composable
fun PillResultScreen(
    detail: PillDetail,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F8))
    ) {
        // 상단 네비게이션
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = onBack) {
                Text("뒤로")
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = detail.name,
                style = MaterialTheme.typography.titleLarge,
                color = Color(0xFF222222)
            )
        }

        // 내용
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PillInfoSection(title = "효능/효과", content = detail.efficacy)
            PillInfoSection(title = "용법/용량", content = detail.dosage)
            PillInfoSection(title = "성분", content = detail.ingredients)
            PillInfoSection(title = "부작용", content = detail.sideEffects)
            PillInfoSection(title = "금기", content = detail.contraindications)
            PillInfoSection(title = "상호작용", content = detail.interactions)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PillInfoSection(
    title: String,
    content: String
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = Color(0xFF333333)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF444444),
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

// ==========================
//  사진 촬영 & 저장
// ==========================
fun takePhoto(
    context: Context,
    imageCapture: ImageCapture,
    onImageSaved: (File) -> Unit
) {
    val outputDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        ?: run {
            Log.e("CameraX", "출력 디렉터리를 가져올 수 없습니다.")
            return
        }

    val fileName = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        .format(System.currentTimeMillis()) + ".jpg"
    val photoFile = File(outputDir, fileName)

    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onError(exc: ImageCaptureException) {
                Log.e("CameraX", "사진 저장 실패: ${exc.message}", exc)
            }

            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                Log.i("CameraX", "사진 저장 성공: ${photoFile.absolutePath}")
                onImageSaved(photoFile)
            }
        }
    )
}

// ==========================
//  EXIF 기준 회전 보정
// ==========================
fun loadRotatedBitmap(path: String): Bitmap? {
    if (path.isEmpty()) return null

    val original = BitmapFactory.decodeFile(path) ?: return null

    return try {
        val exif = ExifInterface(path)
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> Unit
        }

        Bitmap.createBitmap(
            original,
            0,
            0,
            original.width,
            original.height,
            matrix,
            true
        )
    } catch (e: IOException) {
        Log.e("CameraX", "EXIF 읽기 실패: ${e.message}", e)
        original
    }
}

fun uriToTempFile(context: Context, uri: Uri): File {
    val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
    val tempFile = File.createTempFile("gpt_search_", ".jpg", context.cacheDir)

    inputStream.use { input ->
        tempFile.outputStream().use { output ->
            input?.copyTo(output)
        }
    }

    return tempFile
}

// ==========================
//  ML Kit: 비트맵 → 텍스트 인식
// ==========================
fun recognizeTextFromBitmap(
    context: Context,
    bitmap: Bitmap,
    onResult: (String) -> Unit
) {
    try {
        val image = InputImage.fromBitmap(bitmap, 0)

        val recognizer = TextRecognition.getClient(
            KoreanTextRecognizerOptions.Builder().build()
        )

        recognizer
            .process(image)
            .addOnSuccessListener { visionText ->
                onResult(visionText.text ?: "")
            }
            .addOnFailureListener { e ->
                Log.e("MLKit", "텍스트 인식 실패: ${e.message}", e)
                Toast.makeText(context, "텍스트 인식에 실패했습니다.", Toast.LENGTH_SHORT).show()
                onResult("")
            }
    } catch (e: Exception) {
        Log.e("MLKit", "recognizeTextFromBitmap 예외: ${e.message}", e)
        Toast.makeText(context, "텍스트 인식 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
        onResult("")
    }
}

@Composable
fun GptImageSearchScreen(
    onBack: () -> Unit,
    onResult: (PillDetail) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val repository = remember { RetrofitPillRepository() }

    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        selectedImageUri = uri
        errorMessage = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F8))
            .systemBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onBack) {
                Text("뒤로")
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = "GPT 이미지 검색",
                style = MaterialTheme.typography.titleLarge,
                color = Color(0xFF222222)
            )
        }

        Text(
            text = "약 포장지 사진을 첨부하면 GPT가 약 이름을 읽고 검색합니다.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF444444)
        )

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                imagePickerLauncher.launch("image/*")
            }
        ) {
            Text("사진 첨부")
        }

        selectedImageUri?.let { uri ->
            Text(
                text = "사진이 선택되었습니다.",
                color = Color(0xFF333333)
            )
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = selectedImageUri != null && !isLoading,
            onClick = {
                val uri = selectedImageUri ?: return@Button

                coroutineScope.launch {
                    isLoading = true
                    errorMessage = null

                    try {
                        val file = uriToTempFile(context, uri)
                        val result = repository.gptImageSearch(file)
                        onResult(result.toPillDetail())
                    } catch (e: Exception) {
                        Log.e("GPT_SEARCH", "GPT 이미지 검색 실패", e)
                        errorMessage = "이미지 검색 중 오류가 발생했습니다."
                    } finally {
                        isLoading = false
                    }
                }
            }
        ) {
            Text(if (isLoading) "검색 중..." else "GPT로 검색")
        }

        errorMessage?.let {
            Text(
                text = it,
                color = Color.Red
            )
        }
    }
}
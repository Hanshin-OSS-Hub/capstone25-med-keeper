package com.example.pillrecognitionapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale

// ===== Retrofit / 네트워크 관련 =====
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

// ===== ViewModel 관련 =====
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

// ===== ML Kit: Text Recognition (OCR) =====
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions

// ==========================
//  API 응답 모델 (DTO)
// ==========================
data class Ingredient(
    val name: String,
    val amount_mg: Double?
)

data class PillRecognitionResponse(
    val pill_name: String,
    val pill_code: String?,
    val ingredients: List<Ingredient>?,
    val confidence: Double?,
    val color: String?,
    val shape: String?,
    val imprint: String?,
    val warnings: List<String>?
)

// ==========================
//  결과 화면용 도메인 모델
//  (카메라/검색 둘 다 이걸 사용)
// ==========================
data class PillDetail(
    val name: String,
    val efficacy: String,
    val dosage: String,
    val ingredients: String,
    val sideEffects: String,
    val contraindications: String,
    val interactions: String
)

// 카메라 인식 결과 → 화면용 PillDetail 로 변환
fun PillRecognitionResponse.toPillDetail(recognizedText: String? = null): PillDetail {
    val displayName = pill_name.ifBlank { recognizedText ?: "이름 정보 없음" }

    val ingredientText = ingredients
        ?.takeIf { it.isNotEmpty() }
        ?.joinToString(separator = "\n") { ing ->
            if (ing.amount_mg != null) "${ing.name} ${ing.amount_mg}mg" else ing.name
        } ?: "성분 정보가 없습니다."

    val sideEffectText = warnings
        ?.takeIf { it.isNotEmpty() }
        ?.joinToString(separator = "\n") ?: "부작용 정보가 없습니다."

    return PillDetail(
        name = displayName,
        efficacy = "효능/효과 정보는 향후 식약처 DB 연동 시 제공될 예정입니다.",
        dosage = "용법/용량 정보는 향후 식약처 DB 연동 시 제공될 예정입니다.",
        ingredients = ingredientText,
        sideEffects = sideEffectText,
        contraindications = "금기(주의해야 할 대상) 정보는 추후 연동 예정입니다.",
        interactions = "다른 약과의 상호작용 정보는 추후 연동 예정입니다."
    )
}

// 검색 탭에서 사용할 더미 데이터 생성 함수
fun dummyPillDetailFromQuery(query: String): PillDetail {
    val name = if (query.isBlank()) "예시 진통제" else query
    return PillDetail(
        name = name,
        efficacy = "두통, 치통, 근육통 등의 일시적인 통증 완화에 사용되는 일반의약품입니다. (예시 데이터)",
        dosage = "성인 기준 1회 1정, 1일 3회까지 식후에 충분한 물과 함께 복용합니다. (예시 데이터)",
        ingredients = "아세트아미노펜 160mg\n카페인무수물 25mg\n에텐자미드 60mg (예시 데이터)",
        sideEffects = "속쓰림, 메스꺼움, 두통, 어지러움 등이 나타날 수 있습니다. 증상이 심하면 사용을 중단하고 의사와 상담해야 합니다. (예시 데이터)",
        contraindications = "심한 간질환, 위궤양 환자, 약 성분에 알레르기 병력이 있는 경우에는 복용을 피해야 합니다. (예시 데이터)",
        interactions = "다른 진통제(예: 이부프로펜, 나프록센)와 동시 복용 시 과량 복용 위험이 있으므로 주의합니다. (예시 데이터)"
    )
}

// ==========================
//  Retrofit API 정의
// ==========================
interface PillApiService {

    @Multipart
    @POST("/api/v1/pill/recognize")
    suspend fun recognizePill(
        @Part file: MultipartBody.Part
    ): PillRecognitionResponse
}

// ==========================
//  Retrofit 클라이언트
// ==========================
object ApiClient {
    // 에뮬레이터 기준 로컬호스트 주소. 실제 서버 주소로 나중에 교체하면 됨.
    private const val BASE_URL = "http://10.0.2.2:8000/"

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(MoshiConverterFactory.create())
        .build()

    val pillApi: PillApiService = retrofit.create(PillApiService::class.java)
}

// ==========================
//  Repository 계층
// ==========================
interface PillRepository {
    suspend fun recognizePill(imageFile: File): PillRecognitionResponse
}

class RetrofitPillRepository(
    private val api: PillApiService = ApiClient.pillApi
) : PillRepository {

    override suspend fun recognizePill(imageFile: File): PillRecognitionResponse {
        val mimeType = "image/jpeg".toMediaTypeOrNull()
        val requestBody = imageFile.asRequestBody(mimeType)
        val part = MultipartBody.Part.createFormData(
            name = "file",
            filename = imageFile.name,
            body = requestBody
        )
        return api.recognizePill(part)
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

    fun analyze(photoPath: String) {
        val file = File(photoPath)
        if (!file.exists()) {
            uiState = PillUiState.Error("사진 파일을 찾을 수 없습니다.")
            return
        }

        viewModelScope.launch {
            uiState = PillUiState.Loading
            try {
                val result = repository.recognizePill(file)
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
                // 홈 <-> 카메라 전환 상태
                var showCamera by remember { mutableStateOf(false) }

                if (showCamera) {
                    // 카메라 + 미리보기 + 결과 화면
                    AppRoot(
                        onExitCamera = { showCamera = false }
                    )
                } else {
                    // 홈 화면
                    com.example.pillrecognitionapp.ui.home.HomeScreen(
                        onClickSearchCamera = { showCamera = true }
                    )
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
                    viewModel.analyze(route.photoPath)
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
                isAnalyzing -> Text("분석 중입니다...", color = Color.Yellow)
                errorMessage != null -> Text(errorMessage, color = Color.Red)
                else -> Text("분석하기 버튼을 눌러 주세요.", color = Color.White)
            }
        }

        // 하단 버튼들
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0xAA000000))
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

// ==========================
//  Preview용
// ==========================
@ComposePreview(showBackground = true)
@Composable
fun AppRootPreview() {
    PillRecognitionAppTheme {
        PillResultScreen(
            detail = dummyPillDetailFromQuery("예시 약"),
            onBack = {}
        )
    }
}
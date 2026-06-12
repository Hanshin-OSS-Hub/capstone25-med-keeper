import cv2
import numpy as np
import re
from paddleocr import PaddleOCR

# -----------------------------
# 1. OCR 초기화
# -----------------------------
ocr = PaddleOCR(use_textline_orientation=True, lang='korean', enable_mkldnn=False)

# -----------------------------
# 2. DB 연결 (식약처)
# -----------------------------

# -----------------------------
# 3. 전처리
# -----------------------------
def preprocess(img):
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    gray = cv2.equalizeHist(gray)
    blur = cv2.GaussianBlur(gray, (5,5), 0)
    return blur

# -----------------------------
# 4. 위치 필터
# -----------------------------
def position_filter(img):
    return img

# -----------------------------
# 5. 색상 필터
# -----------------------------
def color_filter(img):
    hsv = cv2.cvtColor(img, cv2.COLOR_BGR2HSV)

    lower_red = np.array([0, 70, 50])
    upper_red = np.array([10, 255, 255])

    lower_blue = np.array([100, 70, 50])
    upper_blue = np.array([130, 255, 255])

    mask = cv2.bitwise_or(
        cv2.inRange(hsv, lower_red, upper_red),
        cv2.inRange(hsv, lower_blue, upper_blue)
    )

    return cv2.bitwise_and(img, img, mask=mask)

# -----------------------------
# 6. 한글 필터
# -----------------------------
def filter_korean(text):
    return re.sub(r'[^가-힣0-9]', '', text)

# -----------------------------
# 7. 초성 추출
# -----------------------------
CHOSUNG_LIST = [
    'ㄱ','ㄲ','ㄴ','ㄷ','ㄸ','ㄹ','ㅁ','ㅂ','ㅃ','ㅅ',
    'ㅆ','ㅇ','ㅈ','ㅉ','ㅊ','ㅋ','ㅌ','ㅍ','ㅎ'
]

def get_chosung(text):
    result = []
    for char in text:
        if '가' <= char <= '힣':
            code = ord(char) - ord('가')
            result.append(CHOSUNG_LIST[code // 588])
    return ''.join(result)

# -----------------------------
# 8. Levenshtein
# -----------------------------
def levenshtein(a, b):
    dp = np.zeros((len(a)+1, len(b)+1))

    for i in range(len(a)+1): dp[i][0] = i
    for j in range(len(b)+1): dp[0][j] = j

    for i in range(1, len(a)+1):
        for j in range(1, len(b)+1):
            cost = 0 if a[i-1] == b[j-1] else 1
            dp[i][j] = min(
                dp[i-1][j]+1,
                dp[i][j-1]+1,
                dp[i-1][j-1]+cost
            )
    return dp[-1][-1]

def similarity(a, b):
    return 1 - levenshtein(a, b) / max(len(a), len(b))

# -----------------------------
# 9. n-gram
# -----------------------------
def ngram_similarity(a, b, n=2):
    def grams(x): return {x[i:i+n] for i in range(len(x)-n+1)}
    A, B = grams(a), grams(b)
    return len(A & B) / len(A | B) if A and B else 0

# -----------------------------
# 10. 초성 유사도
# -----------------------------
def chosung_similarity(a, b):
    ca, cb = get_chosung(a), get_chosung(b)
    match = sum(1 for x, y in zip(ca, cb) if x == y)
    return match / max(len(ca), len(cb)) if max(len(ca), len(cb)) > 0 else 0

# -----------------------------
# 11. 통합 유사도
# -----------------------------
def combined_similarity(a, b):
    return (
        similarity(a, b)*0.4 +
        ngram_similarity(a, b)*0.2 +
        chosung_similarity(a, b)*0.3 +
        (min(len(a), len(b))/max(len(a), len(b)))*0.1
    )

# -----------------------------
# 12. 두께 계산
# -----------------------------
def get_thickness_score(img, box):
    if box is None:
        return 0

    x1, y1 = int(box[0][0]), int(box[0][1])
    x2, y2 = int(box[2][0]), int(box[2][1])
    crop = img[y1:y2, x1:x2]

    if crop.size == 0:
        return 0

    gray = cv2.cvtColor(crop, cv2.COLOR_BGR2GRAY)
    _, th = cv2.threshold(gray, 150, 255, cv2.THRESH_BINARY_INV)
    return np.sum(th)

# -----------------------------
# 13. OCR 후보 추출
# -----------------------------
def extract_candidates(img):
    result = ocr.predict(img)

    candidates = []

    if not result:
        return []

    first = result[0]

    texts = first.get("rec_texts", [])
    scores = first.get("rec_scores", [])
    boxes = first.get("rec_polys", [])

    for i, text in enumerate(texts):
        if not text:
            continue

        box = boxes[i] if i < len(boxes) else None
        score = scores[i] if i < len(scores) else 0

        if box is not None:
            box_array = np.array(box)
            h = np.linalg.norm(box_array[0] - box_array[3])
        else:
            h = len(text)

        if len(text) > 15:
            continue

        candidates.append({
            "text": text,
            "box": box,
            "height": h,
            "score": score
        })

    return candidates

# -----------------------------
# 14. 최적 텍스트 선택
# -----------------------------
def select_best_text(candidates, img):
    scored = []

    for c in candidates:
        text = filter_korean(c["text"])
        if len(text) < 2:
            continue

        height = c["height"]
        thickness = get_thickness_score(img, c["box"])

        score = height*0.7 + thickness*0.3
        scored.append((text, score))

    return max(scored, key=lambda x: x[1])[0] if scored else None

# -----------------------------
# 15. FastAPI 전용 실행 함수 (새로 교체)
# -----------------------------
def extract_text_from_image_bytes(image_bytes: bytes) -> str:
    """
    앱에서 전송받은 이미지 바이트를 OpenCV로 읽어 OCR 텍스트를 반환합니다.
    """
    nparr = np.frombuffer(image_bytes, np.uint8)
    img = cv2.imdecode(nparr, cv2.IMREAD_COLOR)

    if img is None:
        print("이미지 디코딩 실패")
        return ""

    roi = position_filter(img)
    #roi = color_filter(roi)
    processed = preprocess(roi)

    processed_3d = cv2.cvtColor(processed, cv2.COLOR_GRAY2BGR)

    candidates = extract_candidates(processed_3d)
    
    if not candidates:
        return ""
        
    print("서버 OCR 후보:", [c["text"] for c in candidates])

    best_text = select_best_text(candidates, roi)
    print("서버 최종 선택 텍스트:", best_text)

    return best_text if best_text else ""


import os
import httpx
from dotenv import load_dotenv

load_dotenv()

SERVICE_KEY = os.getenv("MFDS_APPROVAL_SERVICE_KEY")

URL = "http://apis.data.go.kr/1471000/DrugPrdtPrmsnInfoService07/getDrugPrdtPrmsnInq07"

params = {
    "serviceKey": SERVICE_KEY,
    "pageNo": 1,
    "numOfRows": 3,
    "type": "json",
    "item_name": "타이레놀",
}

print("서비스 키 앞부분:", SERVICE_KEY[:8] if SERVICE_KEY else None)

response = httpx.get(URL, params=params, timeout=20)

print("상태 코드:", response.status_code)
print("요청 URL:", response.url)
print("응답 내용:")
print(response.text[:2000])

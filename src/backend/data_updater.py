import sys
import asyncio
import httpx
import os
from dotenv import load_dotenv
from datetime import datetime
from sqlalchemy.ext.asyncio import create_async_engine, AsyncSession, async_sessionmaker
from sqlalchemy import select, update, insert
from models import Drug, Base
from urllib.parse import quote

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

load_dotenv()

DB_USER = os.getenv("DB_USER")
DB_PASSWORD = os.getenv("DB_PASSWORD")
DB_HOST = os.getenv("DB_HOST")
DB_PORT = os.getenv("DB_PORT")
DB_NAME = os.getenv("DB_NAME")

SERVICE_KEY = os.getenv("MFDS_SERVICE_KEY") # 실제 키

ENCODED_KEY = quote(SERVICE_KEY, safe='')

SERVICE_PATH = "DrbEasyDrugInfoService/getDrbEasyDrugList"

API_URL = (
    f"http://apis.data.go.kr/1471000/DrbEasyDrugInfoService/getDrbEasyDrugList"
    f"?serviceKey={ENCODED_KEY}"
    "&pageNo={page_no}"
    "&numOfRows=100"
    "&type=json"
)
# 식약처 API 엔드포인트 URL로 변경



DATABASE_URL = f"mysql+aiomysql://{DB_USER}:{DB_PASSWORD}@{DB_HOST}:{DB_PORT}/{DB_NAME}"
engine = create_async_engine(DATABASE_URL, echo=False)
AsyncSessionLocal = async_sessionmaker(autocommit=False, autoflush=False, bind=engine)

async def upsert_drug_data(db: AsyncSession, drug_data: dict):
    item_code = drug_data.get('ITEM_SEQ') # 식약처 API에서 제공하는 고유 코드

    update_stmt = (
        update(Drug)
        .where(Drug.id == item_code)
        .values(
            name=drug_data.get('ITEM_NAME'),
            company=drug_data.get('ENTP_NAME'),
            ingredient=drug_data.get('MAIN_ITEM_INGR'), # 예시 필드명
            # ... 나머지 필드 매핑 ...
            updated_at=datetime.utcnow()
        )
    )
    result = await db.execute(update_stmt)

    if result.rowcount == 0:
        insert_data = {
            "id": item_code,
            "name": drug_data.get('ITEM_NAME'),
            "company": drug_data.get('ENTP_NAME'),
            # ... 나머지 필드 매핑 ...
            "updated_at": datetime.utcnow()
        }
        await db.execute(insert(Drug).values(insert_data))

    await db.commit()

async def collect_and_update_drugs(page_no: int = 1, num_of_rows: int = 100):
    params = {
        'serviceKey': SERVICE_KEY,
        'pageNo': page_no,
        'numOfRows': num_of_rows,
        'type': 'json'
    }

    async with httpx.AsyncClient(timeout=60.0) as client:
        print(f"[{datetime.now()}] API 호출 시작 (Page: {page_no})")

        try:
            response = await client.get(API_URL, params=params)
            response.raise_for_status()
            data = response.json()

            items = data.get('body', {}).get('items', [])

            if not items:
                print("수집 완료 또는 더 이상 데이터 없음.")
                return 0

            async with AsyncSessionLocal() as db:
                for item in items:
                    await upsert_drug_data(db, item)

            print(f"Page {page_no}: {len(items)}개 항목 업데이트 완료.")
            return len(items)

        except httpx.HTTPStatusError as e:
            print(f"HTTP 오류 발생: {e.response.status_code}")
        except Exception as e:
            print(f"데이터 수집 중 오류 발생: {e}")

        return 0

async def main_updater():
    page = 1
    total_processed = 0

    while True:
        processed_count = await collect_and_update_drugs(page_no=page)
        total_processed += processed_count

        if processed_count < 100:
            break

        page += 1
        await asyncio.sleep(1)

    print(f"--- 최종 데이터 수집 완료. 총 {total_processed}개 항목 처리 ---")

if __name__ == "__main__":
    asyncio.run(main_updater())

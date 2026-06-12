import sys
import asyncio
import httpx
import os
import re # ✅ 추가: HTML 태그 제거용 정규식 라이브러리
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

SERVICE_KEY = os.getenv("MFDS_SERVICE_KEY")

API_URL = "http://apis.data.go.kr/1471000/DrbEasyDrugInfoService/getDrbEasyDrugList"

DATABASE_URL = f"mysql+aiomysql://{DB_USER}:{DB_PASSWORD}@{DB_HOST}:{DB_PORT}/{DB_NAME}"
engine = create_async_engine(DATABASE_URL, echo=False)
AsyncSessionLocal = async_sessionmaker(autocommit=False, autoflush=False, bind=engine)

# ✅ 1. 텍스트에서 HTML 태그를 깔끔하게 지워주는 함수 추가
def clean_html_tags(text):
    if not text: 
        return None
    # 괄호(< >) 안에 있는 모든 문자열을 제거
    clean_text = re.sub('<.*?>', '', text)
    return clean_text.strip()

async def upsert_drug_data(db: AsyncSession, drug_data: dict):
    item_code = drug_data.get('itemSeq')

    if not item_code:
        return

    # ✅ 2. 식약처 API 데이터를 변수에 담으면서 태그를 싹 지워줍니다.
    effect_text = clean_html_tags(drug_data.get('efcyQesitm'))
    caution_before = clean_html_tags(drug_data.get('atpnWarnQesitm'))
    caution_normal = clean_html_tags(drug_data.get('atpnQesitm'))
    interaction_text = clean_html_tags(drug_data.get('intrcQesitm'))
    side_effect_text = clean_html_tags(drug_data.get('seQesitm'))

    # ✅ 3. Update 구문에 상세 정보 추가
    update_stmt = (
        update(Drug)
        .where(Drug.id == item_code)
        .values(
            name=drug_data.get('itemName'),
            company=drug_data.get('entpName'),
            effect=effect_text,
            caution_before_taking=caution_before,
            caution_nomal=caution_normal,
            interaction=interaction_text,
            side_effect=side_effect_text,
            updated_at=datetime.utcnow()
        )
    )
    result = await db.execute(update_stmt)

    # ✅ 4. Insert 구문에도 상세 정보 추가
    if result.rowcount == 0:
        insert_data = {
            "id": item_code,
            "name": drug_data.get('itemName'),
            "company": drug_data.get('entpName'),
            "effect": effect_text,
            "caution_before_taking": caution_before,
            "caution_nomal": caution_normal,
            "interaction": interaction_text,
            "side_effect": side_effect_text,
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
        print(f"--- 1. API 호출 시도 중... (Page: {page_no}) ---")

        try:
            response = await client.get(API_URL, params=params)
            response.raise_for_status()
            data = response.json()

            print("--- 2. API 응답 성공! DB 저장 시도 중... ---")

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
    print("=== 스크립트 시작! ===")
    asyncio.run(main_updater())

import os
import httpx
from datetime import datetime
from typing import List
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, update, insert
from sqlalchemy.orm import selectinload
from models import Drug, Favorite
from dotenv import load_dotenv

SERVICE_KEY = os.getenv("MFDS_SERVICE_KEY")
APPROVAL_SERVICE_KEY = os.getenv("MFDS_APPROVAL_SERVICE_KEY")

IDENT_SERVICE_KEY = os.getenv("MFDS_IDENT_SERVICE_KEY")
IDENT_API_URL = "http://apis.data.go.kr/1471000/MdcinGrnIdntfcInfoService03/getMdcinGrnIdntfcInfoList03"

API_URL = "http://apis.data.go.kr/1471000/DrbEasyDrugInfoService/getDrbEasyDrugList"
APPROVAL_API_URL = "http://apis.data.go.kr/1471000/DrugPrdtPrmsnInfoService07/getDrugPrdtPrmsnInq07"

def pick_value(item: dict, *keys: str):
    for key in keys:
        value = item.get(key) or item.get(key.lower()) or item.get(key.upper())
        if value:
            return str(value).strip()
    return None


def infer_shape_from_text(text: str | None):
    if not text:
        return None

    shape_words = ["원형", "타원형", "장방형", "삼각형", "사각형", "오각형", "육각형", "팔각형", "반원형"]
    for word in shape_words:
        if word in text:
            return word

    return None


async def fetch_approval_info_from_api(item_code: str | None = None, keyword: str | None = None):
    params = {
        "serviceKey": APPROVAL_SERVICE_KEY,
        "pageNo": 1,
        "numOfRows": 10,
        "type": "json",
    }

    if not APPROVAL_SERVICE_KEY:
        print("품목허가 API 키가 없습니다. .env의 MFDS_APPROVAL_SERVICE_KEY를 확인하세요.")
        return None

    if item_code:
        params["prdlst_Stdr_code"] = item_code

    if keyword:
        params["item_name"] = keyword

    async with httpx.AsyncClient(timeout=10.0) as client:
        try:
            response = await client.get(APPROVAL_API_URL, params=params)
            response.raise_for_status()
            data = response.json()

            body = data.get("body", {})
            items = body.get("items", [])

            if isinstance(items, dict):
                items = [items]

            if not items:
                return None

            return items[0]

        except Exception as e:
            print(f"품목허가 API 호출 에러: {e}")
            return None

async def fetch_and_save_drugs_from_api(db: AsyncSession, keyword: str) -> bool:
    params = {
        'serviceKey': SERVICE_KEY,
        'itemName': keyword,
        'pageNo': 1,
        'numOfRows': 100,
        'type': 'json'
    }

    async with httpx.AsyncClient(timeout=10.0) as client:
        try:
            response = await client.get(API_URL, params=params)
            response.raise_for_status()
            data = response.json()

            body = data.get('response', {}).get('body', {}) if 'response' in data else data.get('body', {})
            items = body.get('items', [])

            if not items:
                return False

            for item in items:
                item_code = item.get('itemSeq')
                if not item_code:
                    continue

                approval_item = await fetch_approval_info_from_api(
                    item_code=item_code,
                    keyword=item.get("itemName")
                )

                ingredient = None
                appearance = None
                shape = None

                update_stmt = (
                    update(Drug)
                    .where(Drug.id == item_code)
                    .values(
                        name=item.get('itemName'),
                        company=item.get('entpName'),
                        ingredient=ingredient,
                        appearance=appearance,
                        shape=shape,
                        updated_at=datetime.utcnow()
                    )
                )
                result = await db.execute(update_stmt)

                if result.rowcount == 0:
                    insert_data = {
                        "id": item_code,
                        "name": item.get('itemName'),
                        "company": item.get('entpName'),
                        "ingredient": ingredient,
                        "appearance": appearance,
                        "shape": shape,
                        "updated_at": datetime.utcnow()
                    }
                    await db.execute(insert(Drug).values(insert_data))

            await db.commit()
            return True

        except Exception as e:
            print(f"식약처 API 호출 에러: {e}")
            return False

async def fetch_identification_info_from_api(item_code: str | None = None, keyword: str | None = None):
    if not IDENT_SERVICE_KEY:
        print("낱알식별 API 키가 없습니다. .env의 MFDS_IDENT_SERVICE_KEY를 확인하세요.")
        return None

    params = {
        "serviceKey": IDENT_SERVICE_KEY,
        "pageNo": 1,
        "numOfRows": 5,
        "type": "json",
    }

    if item_code:
        params["item_seq"] = item_code
    if keyword:
        params["item_name"] = keyword

    async with httpx.AsyncClient(timeout=10.0) as client:
        try:
            response = await client.get(IDENT_API_URL, params=params)
            response.raise_for_status()
            data = response.json()

            body = data.get("body", {})
            items = body.get("items", [])

            if isinstance(items, dict):
                items = [items]

            return items[0] if items else None

        except Exception as e:
            print(f"낱알식별 API 호출 에러: {e}")
            return None


def build_appearance_from_identification(item: dict | None):
    if not item:
        return None

    color1 = pick_value(item, "COLOR_CLASS1", "color_class1")
    color2 = pick_value(item, "COLOR_CLASS2", "color_class2")
    shape = pick_value(item, "DRUG_SHAPE", "drug_shape")
    form = pick_value(item, "FORM_CODE_NAME", "form_code_name")
    print_front = pick_value(item, "PRINT_FRONT", "print_front")
    print_back = pick_value(item, "PRINT_BACK", "print_back")

    parts = []

    colors = " / ".join([c for c in [color1, color2] if c])
    if colors:
        parts.append(f"색상: {colors}")
    if shape:
        parts.append(f"모양: {shape}")
    if form:
        parts.append(f"제형: {form}")

    prints = " / ".join([p for p in [print_front, print_back] if p])
    if prints:
        parts.append(f"식별표시: {prints}")

    return ", ".join(parts) if parts else None

async def fill_missing_approval_fields(db: AsyncSession, drugs: List[Drug]):
    for drug in drugs:
        ingredient = drug.ingredient
        appearance = drug.appearance
        shape = drug.shape

        ident_item = await fetch_identification_info_from_api(
            item_code=drug.id,
            keyword=drug.name
        )

        ident_shape = pick_value(
            ident_item or {},
            "DRUG_SHAPE",
            "drug_shape"
        )

        ident_appearance = build_appearance_from_identification(ident_item)

        appearance_is_wrong_category = drug.appearance and drug.appearance.startswith("[")

        appearance = drug.appearance
        if not appearance or appearance_is_wrong_category:
            appearance = ident_appearance

        shape = drug.shape or ident_shape

        await db.execute(
            update(Drug)
            .where(Drug.id == drug.id)
            .values(
                ingredient=ingredient,
                appearance=appearance,
                shape=shape,
                updated_at=datetime.utcnow()
            )
        )

    await db.commit()

async def search_drugs(db: AsyncSession, keyword: str) -> List[Drug]:
    stmt = select(Drug).filter(Drug.name.contains(keyword)).limit(100)
    result = await db.execute(stmt)
    db_drugs = result.scalars().all()

    if not db_drugs:
        print(f"내 DB에 '{keyword}'(이)가 없어서 식약처에 검색을 요청합니다...")
        success = await fetch_and_save_drugs_from_api(db, keyword)

        if success:
            result = await db.execute(stmt)
            db_drugs = result.scalars().all()
    if db_drugs:
        await fill_missing_approval_fields(db, db_drugs)

        result = await db.execute(stmt)
        db_drugs = result.scalars().all()

    return db_drugs


async def add_favorite(db: AsyncSession, user_id: str, drug_id: str) -> Favorite:
    db_favorite = Favorite(user_id=user_id, drug_id=drug_id)
    db.add(db_favorite)
    await db.commit()
    await db.refresh(db_favorite)
    return db_favorite

async def get_favorites_by_user(db: AsyncSession, user_id: str) -> List[Favorite]:
    stmt = (
        select(Favorite)
        .filter(Favorite.user_id == user_id)
        .options(selectinload(Favorite.drug))
    )
    result = await db.execute(stmt)
    return result.scalars().unique().all()

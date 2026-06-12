from fastapi import APIRouter, Depends, Query, status, HTTPException, UploadFile, File, Form
from sqlalchemy.ext.asyncio import AsyncSession
from typing import Annotated, List, Optional
import os
from services.gpt_service import extract_drug_name_from_image

from database import get_db_async
import schemas, models
import services.drug_service as drug_service
import services.ocr_services as ocr_service
from auth import get_current_active_user

router = APIRouter(
    prefix="/api/v1/drugs",
    tags=["Drugs & Favorites"]
)

@router.post(
    "/recognize",
    response_model=schemas.DrugBase,
    status_code=status.HTTP_200_OK
)
async def recognize_pill_by_image_api(
    db: Annotated[AsyncSession, Depends(get_db_async)],
    file: Annotated[UploadFile, File(description="인식할 알약 이미지 파일")],
    recognized_text: Annotated[Optional[str], Form()] = None
):
    file_contents = await file.read()
    keyword = recognized_text

    # [추가된 핵심 로직] 
    # 앱에서 보낸 글자가 너무 길거나 줄바꿈(\n)이 섞여 있다면 (배경의 쓸데없는 글자까지 다 읽은 경우)
    if keyword and (len(keyword) > 20 or '\n' in keyword):
        print(f"🚫 앱에서 너무 많은 글자를 보냈습니다. (길이: {len(keyword)}자) 서버의 정밀 OCR로 다시 분석합니다...")
        keyword = None # ML Kit의 결과물을 과감히 버립니다!

    # 앱에서 글자를 못 읽었거나 위에서 버려졌다면 서버의 딥러닝(PaddleOCR) 출동!
    if not keyword or not keyword.strip():
        # (주의: PaddleOCR이 동기 함수라 5~10초 정도 시간이 걸릴 수 있습니다)
        keyword = ocr_service.extract_text_from_image_bytes(file_contents)

    # 서버 OCR도 실패하면 기본값 처리
    if not keyword or not keyword.strip():
        raise HTTPException(status_code=400, detail="약 이름을 인식할 수 없습니다. 글자가 잘 보이게 다시 촬영해주세요.")

    print(f"✅ 최종 검색 키워드: {keyword}")

    # 3. 식약처/DB 검색
    drugs = await drug_service.search_drugs(db, keyword)

    if not drugs:
        raise HTTPException(status_code=404, detail=f"'{keyword}'에 대한 약 정보를 찾을 수 없습니다.")

    return drugs[0]

@router.get("/search", response_model=List[schemas.DrugBase])
async def search_drugs_api(
    keyword: Annotated[str, Query(min_length=2, description="검색할 약품 이름의 키워드")],
    db: Annotated[AsyncSession, Depends(get_db_async)]
):
    drugs = await drug_service.search_drugs(db, keyword)
    return drugs

@router.post("/favorites", response_model=schemas.Favorite, status_code=status.HTTP_201_CREATED)
async def add_favorite_api(
    db: Annotated[AsyncSession, Depends(get_db_async)],
    favorite_data: schemas.FavoriteCreate,
    current_user: Annotated[schemas.User, Depends(get_current_active_user)]
):
    try:
        new_favorite = await drug_service.add_favorite(db, current_user.id, favorite_data.drug_id)
        return new_favorite
    except Exception as e:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Could not add favorite. Check drug ID or if it's already added.")


@router.get("/favorites", response_model=List[schemas.Favorite])
async def get_favorites_api(
    db: Annotated[AsyncSession, Depends(get_db_async)],
    current_user: Annotated[schemas.User, Depends(get_current_active_user)]
):
    favorites = await drug_service.get_favorites_by_user(db, current_user.id)
    return favorites

@router.post("/sync-external-data", status_code=status.HTTP_200_OK)
async def trigger_data_sync(
    db: Annotated[AsyncSession, Depends(get_db_async)],
    current_user: Annotated[schemas.User, Depends(get_current_active_user)]
):
    return {"message": "Data synchronization started/completed."}

@router.post(
    "/gpt-image-search",
    response_model=schemas.DrugBase,
    status_code=status.HTTP_200_OK
)
async def search_pill_by_gpt_image_api(
    db: Annotated[AsyncSession, Depends(get_db_async)],
    file: Annotated[UploadFile, File(description="약 포장지 또는 약 사진")]
):
    file_contents = await file.read()
    mime_type = file.content_type or "image/jpeg"

    try :
        drug_name = await extract_drug_name_from_image(file_contents, mime_type)
    except Exception as e:
        print(f"GPT 이미지 분석 실패: {e}")
        raise HTTPException(
            status_code=502,
            detail="GPT 이미지 분석에 실패했습니다. OpenAI API 키, 결제 한도, 사용량을 확인해주세요."
        )

    if not drug_name:
        raise HTTPException(
            status_code=400,
            detail="GPT가 이미지에서 약 이름을 찾지 못했습니다. 약 이름이 잘 보이게 다시 첨부해주세요."
        )

    print(f"GPT가 추출한 약 이름: {drug_name}")

    drugs = await drug_service.search_drugs(db, drug_name)

    if not drugs:
        raise HTTPException(
            status_code=404,
            detail=f"'{drug_name}'에 대한 약 정보를 찾을 수 없습니다."
        )

    return drugs[0]

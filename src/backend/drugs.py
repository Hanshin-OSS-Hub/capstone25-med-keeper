from fastapi import APIRouter, Depends, Query, status, HTTPException, UploadFile, File, Form
from sqlalchemy.ext.asyncio import AsyncSession
from typing import Annotated, List, Optional
import os

from database import get_db_async
import schemas, models
import services.drug_service as drug_service
from auth import get_current_active_user

router = APIRouter(
    prefix="/api/v1/drugs",
    tags=["Drugs & Favorites"]
)

@router.post(
    "/recognize",
    response_model=schemas.PillRecognitionResponse,
    status_code=status.HTTP_200_OK
)
async def recognize_pill_by_image_api(
    current_user: Annotated[schemas.User, Depends(get_current_active_user)],
    db: Annotated[AsyncSession, Depends(get_db_async)],
    file: Annotated[UploadFile, File(description="인식할 알약 이미지 파일")],
    recognized_text: Annotated[Optional[str], Form()] = None
):

    #테스트용 더미 데이터
    file_contents = await file.read()

    return schemas.PillRecognitionResponse(
        pill_name="아세트아미노펜 650mg",
        pill_code="198804008",
        ingredients=[
            schemas.Ingredient(name="아세트아미노펜", amount_mg=650.0)
        ],
        confidence=0.95,
        color="흰색",
        shape="장방형",
        imprint="APAP 650",
        warnings=["심각한 간 질환 환자는 복용 금지"],
        recognized_text=recognized_text # OCR로 인식된 텍스트를 포함하여 반환 (앱에서 활용)
    )

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

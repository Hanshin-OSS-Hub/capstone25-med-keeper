from pydantic import BaseModel, Field
from typing import Optional, List
from datetime import datetime

class Config:
    from_attributes = True

#1. 약품 데이터 캐싱 스키마(drugs)
class DrugBase(BaseModel):
    id: str
    name: str
    company: Optional[str] = None
    ingredient: Optional[str] = None
    appearance: Optional[str] = None
    shape: Optional[str] = None
    effect: Optional[str] = None
    caution_before_taking: Optional[str] = None
    caution_nomal: Optional[str] = None
    interaction: Optional[str] = None
    side_effect: Optional[str] = None
    updated_at: datetime

    class Config:
        from_attributes = True

#2. 사용자 계정 스키마(users)
class UserCreate(BaseModel):
    nickname: str

class User(BaseModel):
    id: str
    nickname: str

    class Config:
        from_attributes = True

#3. 즐겨찾기 스키마(favorites)
class FavoriteCreate(BaseModel):
    drug_id: str

class Favorite(BaseModel):
    id: int
    user_id: str
    drug_id: str
    created_at: datetime

    drug: DrugBase

    class Config:
        from_attributes = True

#4. 개인 맞춤 설정 정보 스키마(user_settings)
class UserSettingUpdate(BaseModel):
    allergy_info: Optional[str] = None
    preferred_search_type: Optional[str] = None

class UserSetting(BaseModel):
    user_id: str
    allergy_info: str
    preferred_search_type: str

    class Config:
        from_attributes = True

#5. 알약 인식 응답 스키마

# Ingredient (성분 정보)
class Ingredient(BaseModel):
    name: str = Field(description="성분 이름")
    amount_mg: Optional[float] = Field(None, description="성분 용량 (mg)")

    class Config:
        from_attributes = True

# PillRecognitionResponse (알약 인식 결과 전체)
class PillRecognitionResponse(BaseModel):
    pill_name: str = Field(description="최종 검색된 약품 이름")
    pill_code: Optional[str] = Field(None, description="식별 코드 (품목 기준 코드)")
    ingredients: Optional[List[Ingredient]] = Field(None, description="주요 성분 목록")
    confidence: Optional[float] = Field(None, description="인식 정확도 (0.0 ~ 1.0)")
    color: Optional[str] = Field(None, description="인식된 알약 색상")
    shape: Optional[str] = Field(None, description="인식된 알약 모양")
    imprint: Optional[str] = Field(None, description="인식된 각인 텍스트 (OCR 결과)")
    warnings: Optional[List[str]] = Field(None, description="주요 경고/주의 사항")

    recognized_text: Optional[str] = Field(None, description="앱에서 전달받은 OCR 원본 텍스트")

    class Config:
        from_attributes = True

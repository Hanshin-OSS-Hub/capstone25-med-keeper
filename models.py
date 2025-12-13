from sqlalchemy import Column, Integer, String, Text, ForeignKey, DateTime
from sqlalchemy.orm import relationship
from datetime import datetime
from database import Base

#1. 사용자 계정 모델(users)
class User(Base):
    __tablename__ = "users"

    id = Column(String(255), primary_key=True, index=True, comment="Firebase UID")
    nickname = Column(String(255), unique=True)
    favorites = relationship("Favorite", back_populates="user")
    settings = relationship("UserSetting", back_populates="user", uselist=False)

#2. 즐겨찾기 모델(favorites)
class Favorite(Base):
    __tablename__ = "favorites"

    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(String(255), ForeignKey("users.id"))
    drug_id = Column(String(255), ForeignKey("drugs.id"))

    created_at = Column(DateTime, default=datetime.utcnow)

    user = relationship("User", back_populates="favorites")
    drug = relationship("Drug", back_populates="favorites")

#3. 약품 데이터 캐싱 모델 (drugs)
class Drug(Base):
    __tablename__ = "drugs"

    id = Column(String(255), primary_key=True, index=True, comment="식약처 품목기준코드")
    name = Column(String(255), index=True, comment="이름")
    company = Column(String(255), comment="제조/수입사")
    ingredient = Column(Text, comment="성분/함량, 첨가물")
    appearance = Column(String(255), comment="성상")
    shape = Column(String(255), comment="모양")
    effect = Column(Text, comment="효능")
    caution_before_taking = Column(Text, comment="복용 전 주의사항")
    caution_nomal = Column(Text, comment="일반 주의사항")
    interaction = Column(Text, comment="상호작용")
    side_effect = Column(Text, comment="부작용")

    updated_at = Column(DateTime, default=datetime.utcnow)

    favorites = relationship("Favorite", back_populates="drug")

#4. 개인 맞춤 설정 정보 모델(user_settings)
class UserSetting(Base):
    __tablename__ = "user_settings"

    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(String(255), ForeignKey("users.id"), unique=True)

    allergy_info = Column(Text, default="", comment="알레르기 정보")
    preferred_search_type = Column(String(255), default="name", comment="선호 검색 타입")

    user = relationship("User", back_populates="settings")

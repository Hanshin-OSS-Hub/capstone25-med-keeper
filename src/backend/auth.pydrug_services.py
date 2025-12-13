from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, func
from sqlalchemy.orm import selectinload
from typing import List
from models import Drug, Favorite
from schemas import DrugBase, FavoriteCreate

async def search_drugs(db: AsyncSession, keyword: str) -> List[Drug]:
    stmt = (
        select(Drug)
        .filter(Drug.name.like(f"%{keyword}%"))
        .limit(100)
    )

    result = await db.excute(stmt)

    return result.scalars().all()

async def add_favorite(db: AsyncSession, user_id: str, drug_id: str) -> Favorite:
    db_favorite = Favorite(
        user_id=user_id,
        drug_id=drug_id
    )

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

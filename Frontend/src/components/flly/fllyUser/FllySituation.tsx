import React from "react";
import style from "./FllySituation.module.css";
import { useState } from "react";
import { useRecoilState } from "recoil";
import { situationState } from "@/recoil/fllyRecoil";
import Image from "next/image";

const FllySeller = () => {
  const [situation, setSituation] = useRecoilState(situationState);
  const [selected, setSelected] = useState<string>("");
  const selectList = ["축하", "응원", "건강", "사랑", "감사", "사과", "위로", "선택안함"];
  const imgList = ["/img/icon/situation01.png", "/img/icon/situation02.png", "/img/icon/situation03.png", "/img/icon/situation04.png", "/img/icon/situation05.png", "/img/icon/situation06.png", "/img/icon/situation07.png", "/img/icon/situation08.png"];
  const handleSelect = (e:string) => {
    setSelected(e);
    setSituation(e);
  };

  return (
    <>
      <div className={style.fllyBox}>
        <div className={style.contentBox}>
          <div className={style.progress}>
            <div className={style.progressBar}><div className={style.barCompleted}></div><div className={style.barWating}></div></div>
            <div className={style.progressNum}>1/3</div>
          </div>
          <div className={style.headerTitle}>
            <div className={style.stage}>1</div>
            <div className={style.guide}>구매 목적을 선택해주세요.</div>
          </div>
          <div className={style.selectBox}>
            {selectList.map((item, index) => (
              <div key={index} className={selected==item? style.selectedCard: style.selectCard} onClick={() => {handleSelect(item)}}>
                <div className={style.selectWord}>{item}</div>
                <Image src={imgList[index]} width={60} height={60} alt="아이콘" />
              </div>
            ))}            
          </div>
          <div className={style.nextBtn}>다음</div>
        </div>
      </div>
    </>
  );
};

export default FllySeller;

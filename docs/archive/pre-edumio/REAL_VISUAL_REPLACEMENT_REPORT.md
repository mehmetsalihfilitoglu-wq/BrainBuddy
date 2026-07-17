# Real Visual Content Replacement — Report

## Summary

| Metric | Count |
|--------|-------|
| Visual questions with **meaningful** matching asset | 962 |
| Visual questions still using placeholder (missing file) | 0 |
| JSON question updates (imageAsset set/changed) | 245 (cumulative over runs) |
| JSON files modified | 324 |
| New asset images created | 2 |

**Validation:** `scripts/validate_image_assets.py` — all imageAsset paths resolve (1207 questions with imageAsset).

## Files updated

- `app\src\main\assets\lgs_import\english1\lgs_eng1_pack_001.json`
- `app\src\main\assets\lgs_import\english1\lgs_eng1_pack_002.json`
- `app\src\main\assets\lgs_import\english1\lgs_eng1_pack_003.json`
- `app\src\main\assets\lgs_import\english1\lgs_eng1_pack_004.json`
- `app\src\main\assets\lgs_import\english1\lgs_eng1_pack_005.json`
- `app\src\main\assets\lgs_import\english1\lgs_eng1_pack_006.json`
- `app\src\main\assets\lgs_import\english1\lgs_eng1_pack_011.json`
- `app\src\main\assets\lgs_import\english1\lgs_eng1_pack_012.json`
- `app\src\main\assets\lgs_import\english1\lgs_eng1_pack_013.json`
- `app\src\main\assets\lgs_import\english1\lgs_eng1_pack_014.json`
- `app\src\main\assets\lgs_import\english1\lgs_eng1_pack_015.json`
- `app\src\main\assets\lgs_import\english1\lgs_eng1_pack_016.json`
- `app\src\main\assets\lgs_import\english3\lgs_eng3_pack_021.json`
- `app\src\main\assets\lgs_import\english3\lgs_eng3_pack_022.json`
- `app\src\main\assets\lgs_import\english3\lgs_eng3_pack_023.json`
- `app\src\main\assets\lgs_import\english3\lgs_eng3_pack_024.json`
- `app\src\main\assets\lgs_import\english3\lgs_eng3_pack_025.json`
- `app\src\main\assets\lgs_import\english3\lgs_eng3_pack_031.json`
- `app\src\main\assets\lgs_import\english3\lgs_eng3_pack_032.json`
- `app\src\main\assets\lgs_import\english3\lgs_eng3_pack_033.json`
- `app\src\main\assets\lgs_import\english3\lgs_eng3_pack_034.json`
- `app\src\main\assets\lgs_import\english3\lgs_eng3_pack_035.json`
- `app\src\main\assets\lgs_import\english4\lgs_eng4_pack_011.json`
- `app\src\main\assets\lgs_import\english4\lgs_eng4_pack_012.json`
- `app\src\main\assets\lgs_import\english4\lgs_eng4_pack_013.json`
- `app\src\main\assets\lgs_import\english4\lgs_eng4_pack_014.json`
- `app\src\main\assets\lgs_import\english4\lgs_eng4_pack_015.json`
- `app\src\main\assets\lgs_import\english4\lgs_eng4_pack_026.json`
- `app\src\main\assets\lgs_import\english4\lgs_eng4_pack_027.json`
- `app\src\main\assets\lgs_import\english4\lgs_eng4_pack_028.json`
- `app\src\main\assets\lgs_import\english4\lgs_eng4_pack_029.json`
- `app\src\main\assets\lgs_import\english4\lgs_eng4_pack_030.json`
- `app\src\main\assets\lgs_import\english4\lgs_eng4_pack_046.json`
- `app\src\main\assets\lgs_import\english4\lgs_eng4_pack_047.json`
- `app\src\main\assets\lgs_import\english4\lgs_eng4_pack_048.json`
- `app\src\main\assets\lgs_import\english4\lgs_eng4_pack_049.json`
- `app\src\main\assets\lgs_import\english4\lgs_eng4_pack_050.json`
- `app\src\main\assets\lgs_import\english4\lgs_eng4_visual_demo.json`
- `app\src\main\assets\lgs_import\fen3\lgs_fen3_pack_001.json`
- `app\src\main\assets\lgs_import\fen3\lgs_fen3_pack_002.json`
- `app\src\main\assets\lgs_import\fen3\lgs_fen3_pack_003.json`
- `app\src\main\assets\lgs_import\fen3\lgs_fen3_pack_004.json`
- `app\src\main\assets\lgs_import\fen3\lgs_fen3_pack_005.json`
- `app\src\main\assets\lgs_import\fen3\lgs_fen3_pack_006.json`
- `app\src\main\assets\lgs_import\fen3\lgs_fen3_pack_007.json`
- `app\src\main\assets\lgs_import\fen3\lgs_fen3_pack_008.json`
- `app\src\main\assets\lgs_import\fen3\lgs_fen3_pack_009.json`
- `app\src\main\assets\lgs_import\fen3\lgs_fen3_pack_026.json`
- `app\src\main\assets\lgs_import\fen3\lgs_fen3_pack_027.json`
- `app\src\main\assets\lgs_import\fen3\lgs_fen3_pack_028.json`
- `app\src\main\assets\lgs_import\fen3\lgs_fen3_pack_029.json`
- `app\src\main\assets\lgs_import\fen3\lgs_fen3_pack_030.json`
- `app\src\main\assets\lgs_import\fen3\lgs_fen3_pack_031.json`
- `app\src\main\assets\lgs_import\fen3\lgs_fen3_pack_032.json`
- `app\src\main\assets\lgs_import\fen3\lgs_fen3_pack_033.json`
- `app\src\main\assets\lgs_import\fen3\lgs_fen3_pack_042.json`
- `app\src\main\assets\lgs_import\fen3\lgs_fen3_pack_043.json`
- `app\src\main\assets\lgs_import\fen3\lgs_fen3_pack_044.json`
- `app\src\main\assets\lgs_import\fen3\lgs_fen3_pack_045.json`
- `app\src\main\assets\lgs_import\fen3\lgs_fen3_pack_046.json`
- `app\src\main\assets\lgs_import\fen3\lgs_fen3_pack_047.json`
- `app\src\main\assets\lgs_import\fen3\lgs_fen3_pack_048.json`
- `app\src\main\assets\lgs_import\fen3\lgs_fen3_pack_049.json`
- `app\src\main\assets\lgs_import\fen3\lgs_fen3_pack_050.json`
- `app\src\main\assets\lgs_import\fen4\lgs_fen4_pack_008.json`
- `app\src\main\assets\lgs_import\fen4\lgs_fen4_pack_009.json`
- `app\src\main\assets\lgs_import\fen4\lgs_fen4_pack_010.json`
- `app\src\main\assets\lgs_import\fen4\lgs_fen4_pack_011.json`
- `app\src\main\assets\lgs_import\fen4\lgs_fen4_pack_012.json`
- `app\src\main\assets\lgs_import\fen4\lgs_fen4_pack_013.json`
- `app\src\main\assets\lgs_import\fen4\lgs_fen4_pack_014.json`
- `app\src\main\assets\lgs_import\fen4\lgs_fen4_pack_015.json`
- `app\src\main\assets\lgs_import\fen4\lgs_fen4_pack_016.json`
- `app\src\main\assets\lgs_import\fen4\lgs_fen4_pack_017.json`
- `app\src\main\assets\lgs_import\fen4\lgs_fen4_pack_018.json`
- `app\src\main\assets\lgs_import\fen4\lgs_fen4_pack_019.json`
- `app\src\main\assets\lgs_import\fen4\lgs_fen4_pack_020.json`
- `app\src\main\assets\lgs_import\fen4\lgs_fen4_pack_021.json`
- `app\src\main\assets\lgs_import\fen4\lgs_fen4_pack_022.json`
- `app\src\main\assets\lgs_import\fen4\lgs_fen4_pack_023.json`
- `app\src\main\assets\lgs_import\fen4\lgs_fen4_pack_024.json`
- `app\src\main\assets\lgs_import\fen4\lgs_fen4_pack_025.json`
- `app\src\main\assets\lgs_import\fen4\lgs_fen4_pack_026.json`
- `app\src\main\assets\lgs_import\fen4\lgs_fen4_pack_027.json`
- `app\src\main\assets\lgs_import\fen4\lgs_fen4_pack_028.json`
- `app\src\main\assets\lgs_import\fen4\lgs_fen4_pack_029.json`
- `app\src\main\assets\lgs_import\fen5\lgs_fen5_pack_016.json`
- `app\src\main\assets\lgs_import\fen5\lgs_fen5_pack_017.json`
- `app\src\main\assets\lgs_import\fen5\lgs_fen5_pack_018.json`
- `app\src\main\assets\lgs_import\fen5\lgs_fen5_pack_019.json`
- `app\src\main\assets\lgs_import\fen5\lgs_fen5_pack_020.json`
- `app\src\main\assets\lgs_import\fen5\lgs_fen5_pack_021.json`
- `app\src\main\assets\lgs_import\fen5\lgs_fen5_pack_022.json`
- `app\src\main\assets\lgs_import\fen5\lgs_fen5_pack_023.json`
- `app\src\main\assets\lgs_import\fen5\lgs_fen5_pack_024.json`
- `app\src\main\assets\lgs_import\fen5\lgs_fen5_pack_025.json`
- `app\src\main\assets\lgs_import\fen5\lgs_fen5_pack_026.json`
- `app\src\main\assets\lgs_import\fen5\lgs_fen5_pack_027.json`
- `app\src\main\assets\lgs_import\fen5\lgs_fen5_pack_028.json`
- `app\src\main\assets\lgs_import\fen5\lgs_fen5_pack_029.json`
- `app\src\main\assets\lgs_import\fen5\lgs_fen5_pack_044.json`
- `app\src\main\assets\lgs_import\fen5\lgs_fen5_pack_045.json`
- `app\src\main\assets\lgs_import\fen5\lgs_fen5_pack_046.json`
- `app\src\main\assets\lgs_import\fen5\lgs_fen5_pack_047.json`
- `app\src\main\assets\lgs_import\fen5\lgs_fen5_pack_048.json`
- `app\src\main\assets\lgs_import\fen5\lgs_fen5_pack_049.json`
- `app\src\main\assets\lgs_import\fen5\lgs_fen5_pack_050.json`
- `app\src\main\assets\lgs_import\fen6\lgs_fen6_pack_001.json`
- `app\src\main\assets\lgs_import\fen6\lgs_fen6_pack_006.json`
- `app\src\main\assets\lgs_import\fen6\lgs_fen6_pack_010.json`
- `app\src\main\assets\lgs_import\fen6\lgs_fen6_pack_015.json`
- `app\src\main\assets\lgs_import\fen6\lgs_fen6_pack_019.json`
- `app\src\main\assets\lgs_import\fen6\lgs_fen6_pack_024.json`
- `app\src\main\assets\lgs_import\fen6\lgs_fen6_pack_028.json`
- `app\src\main\assets\lgs_import\fen6\lgs_fen6_pack_033.json`
- `app\src\main\assets\lgs_import\fen6\lgs_fen6_pack_037.json`
- `app\src\main\assets\lgs_import\fen6\lgs_fen6_pack_042.json`
- `app\src\main\assets\lgs_import\fen6\lgs_fen6_pack_046.json`
- `app\src\main\assets\lgs_import\fen7\lgs_fen7_pack_001.json`
- `app\src\main\assets\lgs_import\fen7\lgs_fen7_pack_002.json`
- `app\src\main\assets\lgs_import\fen7\lgs_fen7_pack_004.json`
- `app\src\main\assets\lgs_import\fen7\lgs_fen7_pack_005.json`
- `app\src\main\assets\lgs_import\fen7\lgs_fen7_pack_007.json`
- `app\src\main\assets\lgs_import\fen7\lgs_fen7_pack_009.json`
- `app\src\main\assets\lgs_import\fen7\lgs_fen7_pack_010.json`
- `app\src\main\assets\lgs_import\fen7\lgs_fen7_pack_012.json`
- `app\src\main\assets\lgs_import\fen7\lgs_fen7_pack_013.json`
- `app\src\main\assets\lgs_import\fen7\lgs_fen7_pack_015.json`
- `app\src\main\assets\lgs_import\fen7\lgs_fen7_pack_016.json`
- `app\src\main\assets\lgs_import\fen7\lgs_fen7_pack_018.json`
- `app\src\main\assets\lgs_import\fen7\lgs_fen7_pack_019.json`
- `app\src\main\assets\lgs_import\fen7\lgs_fen7_pack_021.json`
- `app\src\main\assets\lgs_import\fen7\lgs_fen7_pack_023.json`
- `app\src\main\assets\lgs_import\fen7\lgs_fen7_pack_024.json`
- `app\src\main\assets\lgs_import\fen7\lgs_fen7_pack_026.json`
- `app\src\main\assets\lgs_import\fen7\lgs_fen7_pack_027.json`
- `app\src\main\assets\lgs_import\fen7\lgs_fen7_pack_029.json`
- `app\src\main\assets\lgs_import\fen7\lgs_fen7_pack_030.json`
- `app\src\main\assets\lgs_import\fen7\lgs_fen7_pack_032.json`
- `app\src\main\assets\lgs_import\fen7\lgs_fen7_pack_033.json`
- `app\src\main\assets\lgs_import\fen7\lgs_fen7_pack_035.json`
- `app\src\main\assets\lgs_import\fen7\lgs_fen7_pack_037.json`
- `app\src\main\assets\lgs_import\fen7\lgs_fen7_pack_038.json`
- `app\src\main\assets\lgs_import\fen7\lgs_fen7_pack_040.json`
- `app\src\main\assets\lgs_import\fen7\lgs_fen7_pack_041.json`
- `app\src\main\assets\lgs_import\fen7\lgs_fen7_pack_043.json`
- `app\src\main\assets\lgs_import\fen7\lgs_fen7_pack_044.json`
- `app\src\main\assets\lgs_import\fen7\lgs_fen7_pack_046.json`
- `app\src\main\assets\lgs_import\fen7\lgs_fen7_pack_047.json`
- `app\src\main\assets\lgs_import\fen7\lgs_fen7_pack_049.json`
- `app\src\main\assets\lgs_import\fen\lgs_fen_pack_basinc_001.json`
- `app\src\main\assets\lgs_import\fen\lgs_fen_pack_basinc_002.json`
- `app\src\main\assets\lgs_import\fen\lgs_fen_pack_basinc_003.json`
- `app\src\main\assets\lgs_import\fen\lgs_fen_pack_basinc_004.json`
- `app\src\main\assets\lgs_import\fen\lgs_fen_pack_basinc_005.json`
- `app\src\main\assets\lgs_import\fen\lgs_fen_pack_basinc_006.json`
- `app\src\main\assets\lgs_import\fen\lgs_fen_pack_elektrik_003.json`
- `app\src\main\assets\lgs_import\fen\lgs_fen_pack_enerji_donusumleri_003.json`
- `app\src\main\assets\lgs_import\fen\lgs_fen_pack_enerji_donusumleri_005.json`
- `app\src\main\assets\lgs_import\fen\lgs_fen_pack_madde_ve_endustri_001.json`
- `app\src\main\assets\lgs_import\fen\lgs_fen_pack_madde_ve_endustri_002.json`
- `app\src\main\assets\lgs_import\fen\lgs_fen_pack_madde_ve_endustri_003.json`
- `app\src\main\assets\lgs_import\fen\lgs_fen_pack_madde_ve_endustri_004.json`
- `app\src\main\assets\lgs_import\fen\lgs_fen_pack_madde_ve_endustri_005.json`
- `app\src\main\assets\lgs_import\fen\lgs_fen_pack_madde_ve_endustri_006.json`
- `app\src\main\assets\lgs_import\fen\lgs_fen_pack_madde_ve_endustri_007.json`
- `app\src\main\assets\lgs_import\fen\lgs_fen_pack_madde_ve_endustri_008.json`
- `app\src\main\assets\lgs_import\fen\lgs_fen_pack_mevsimler_ve_iklim_001.json`
- `app\src\main\assets\lgs_import\fen\lgs_fen_pack_mevsimler_ve_iklim_002.json`
- `app\src\main\assets\lgs_import\fen\lgs_fen_pack_mevsimler_ve_iklim_006.json`
- `app\src\main\assets\lgs_import\hayat1\lgs_hayat1_pack_001.json`
- `app\src\main\assets\lgs_import\hayat1\lgs_hayat1_pack_002.json`
- `app\src\main\assets\lgs_import\hayat1\lgs_hayat1_pack_003.json`
- `app\src\main\assets\lgs_import\hayat1\lgs_hayat1_pack_004.json`
- `app\src\main\assets\lgs_import\hayat1\lgs_hayat1_pack_005.json`
- `app\src\main\assets\lgs_import\hayat1\lgs_hayat1_pack_006.json`
- `app\src\main\assets\lgs_import\hayat1\lgs_hayat1_pack_010.json`
- `app\src\main\assets\lgs_import\hayat1\lgs_hayat1_pack_011.json`
- `app\src\main\assets\lgs_import\hayat1\lgs_hayat1_pack_012.json`
- `app\src\main\assets\lgs_import\hayat1\lgs_hayat1_pack_013.json`
- `app\src\main\assets\lgs_import\hayat1\lgs_hayat1_pack_014.json`
- `app\src\main\assets\lgs_import\hayat1\lgs_hayat1_pack_015.json`
- `app\src\main\assets\lgs_import\hayat1\lgs_hayat1_pack_016.json`
- `app\src\main\assets\lgs_import\hayat2\lgs_hayat2_pack_042.json`
- `app\src\main\assets\lgs_import\hayat2\lgs_hayat2_pack_043.json`
- `app\src\main\assets\lgs_import\hayat2\lgs_hayat2_pack_044.json`
- `app\src\main\assets\lgs_import\hayat2\lgs_hayat2_pack_045.json`
- `app\src\main\assets\lgs_import\hayat2\lgs_hayat2_pack_046.json`
- `app\src\main\assets\lgs_import\hayat2\lgs_hayat2_pack_047.json`
- `app\src\main\assets\lgs_import\hayat2\lgs_hayat2_pack_048.json`
- `app\src\main\assets\lgs_import\hayat2\lgs_hayat2_pack_049.json`
- `app\src\main\assets\lgs_import\hayat2\lgs_hayat2_pack_050.json`
- `app\src\main\assets\lgs_import\lgs_fen.json`
- `app\src\main\assets\lgs_import\lgs_inkilap.json`
- `app\src\main\assets\lgs_import\lgs_mat.json`
- `app\src\main\assets\lgs_import\lgs_turkce.json`
- `app\src\main\assets\lgs_import\mat1\lgs_mat1_pack_001.json`
- `app\src\main\assets\lgs_import\mat1\lgs_mat1_pack_002.json`
- `app\src\main\assets\lgs_import\mat1\lgs_mat1_pack_003.json`
- `app\src\main\assets\lgs_import\mat1\lgs_mat1_pack_004.json`
- `app\src\main\assets\lgs_import\mat1\lgs_mat1_pack_006.json`
- `app\src\main\assets\lgs_import\mat1\lgs_mat1_pack_007.json`
- `app\src\main\assets\lgs_import\mat1\lgs_mat1_pack_008.json`
- `app\src\main\assets\lgs_import\mat1\lgs_mat1_pack_009.json`
- `app\src\main\assets\lgs_import\mat1\lgs_mat1_pack_010.json`
- `app\src\main\assets\lgs_import\mat1\lgs_mat1_pack_011.json`
- `app\src\main\assets\lgs_import\mat1\lgs_mat1_pack_012.json`
- `app\src\main\assets\lgs_import\mat1\lgs_mat1_pack_013.json`
- `app\src\main\assets\lgs_import\mat1\lgs_mat1_pack_014.json`
- `app\src\main\assets\lgs_import\mat1\lgs_mat1_pack_015.json`
- `app\src\main\assets\lgs_import\mat1\lgs_mat1_pack_016.json`
- `app\src\main\assets\lgs_import\mat1\lgs_mat1_pack_017.json`
- `app\src\main\assets\lgs_import\mat1\lgs_mat1_pack_018.json`
- `app\src\main\assets\lgs_import\mat1\lgs_mat1_pack_019.json`
- `app\src\main\assets\lgs_import\mat1\lgs_mat1_pack_020.json`
- `app\src\main\assets\lgs_import\mat1\lgs_mat1_pack_021.json`
- `app\src\main\assets\lgs_import\mat3\lgs_mat3_pack_047.json`
- `app\src\main\assets\lgs_import\mat3\lgs_mat3_pack_048.json`
- `app\src\main\assets\lgs_import\mat3\lgs_mat3_pack_049.json`
- `app\src\main\assets\lgs_import\mat3\lgs_mat3_pack_050.json`
- `app\src\main\assets\lgs_import\mat5\lgs_mat5_pack_003.json`
- `app\src\main\assets\lgs_import\mat5\lgs_mat5_pack_007.json`
- `app\src\main\assets\lgs_import\mat5\lgs_mat5_pack_011.json`
- `app\src\main\assets\lgs_import\mat5\lgs_mat5_pack_015.json`
- `app\src\main\assets\lgs_import\mat5\lgs_mat5_pack_019.json`
- `app\src\main\assets\lgs_import\mat5\lgs_mat5_pack_023.json`
- `app\src\main\assets\lgs_import\mat5\lgs_mat5_pack_027.json`
- `app\src\main\assets\lgs_import\mat5\lgs_mat5_pack_031.json`
- `app\src\main\assets\lgs_import\mat5\lgs_mat5_pack_035.json`
- `app\src\main\assets\lgs_import\mat5\lgs_mat5_pack_039.json`
- `app\src\main\assets\lgs_import\mat5\lgs_mat5_pack_043.json`
- `app\src\main\assets\lgs_import\mat5\lgs_mat5_pack_047.json`
- `app\src\main\assets\lgs_import\mat\lgs_mat_gold_002.json`
- `app\src\main\assets\lgs_import\mat\lgs_mat_gold_005.json`
- `app\src\main\assets\lgs_import\sosyal5\lgs_sosyal5_pack_029.json`
- `app\src\main\assets\lgs_import\sosyal5\lgs_sosyal5_pack_030.json`
- `app\src\main\assets\lgs_import\sosyal5\lgs_sosyal5_pack_031.json`
- `app\src\main\assets\lgs_import\sosyal5\lgs_sosyal5_pack_032.json`
- `app\src\main\assets\lgs_import\sosyal5\lgs_sosyal5_pack_033.json`
- `app\src\main\assets\lgs_import\sosyal5\lgs_sosyal5_pack_034.json`
- `app\src\main\assets\lgs_import\sosyal5\lgs_sosyal5_pack_035.json`
- `app\src\main\assets\lgs_import\sosyal5\lgs_sosyal5_pack_036.json`
- `app\src\main\assets\lgs_import\sosyal6\lgs_sosyal6_pack_003.json`
- `app\src\main\assets\lgs_import\sosyal6\lgs_sosyal6_pack_007.json`
- `app\src\main\assets\lgs_import\sosyal6\lgs_sosyal6_pack_011.json`
- `app\src\main\assets\lgs_import\sosyal6\lgs_sosyal6_pack_014.json`
- `app\src\main\assets\lgs_import\sosyal6\lgs_sosyal6_pack_018.json`
- `app\src\main\assets\lgs_import\sosyal6\lgs_sosyal6_pack_022.json`
- `app\src\main\assets\lgs_import\sosyal6\lgs_sosyal6_pack_026.json`
- `app\src\main\assets\lgs_import\sosyal6\lgs_sosyal6_pack_030.json`
- `app\src\main\assets\lgs_import\sosyal6\lgs_sosyal6_pack_033.json`
- `app\src\main\assets\lgs_import\sosyal6\lgs_sosyal6_pack_037.json`
- `app\src\main\assets\lgs_import\sosyal6\lgs_sosyal6_pack_041.json`
- `app\src\main\assets\lgs_import\sosyal6\lgs_sosyal6_pack_045.json`
- `app\src\main\assets\lgs_import\sosyal6\lgs_sosyal6_pack_049.json`
- `app\src\main\assets\lgs_import\turkce1\lgs_turkce1_pack_001.json`
- `app\src\main\assets\lgs_import\turkce1\lgs_turkce1_pack_002.json`
- `app\src\main\assets\lgs_import\turkce1\lgs_turkce1_pack_003.json`
- `app\src\main\assets\lgs_import\turkce1\lgs_turkce1_pack_004.json`
- `app\src\main\assets\lgs_import\turkce1\lgs_turkce1_pack_005.json`
- `app\src\main\assets\lgs_import\turkce1\lgs_turkce1_pack_006.json`
- `app\src\main\assets\lgs_import\turkce1\lgs_turkce1_pack_007.json`
- `app\src\main\assets\lgs_import\turkce1\lgs_turkce1_pack_010.json`
- `app\src\main\assets\lgs_import\turkce1\lgs_turkce1_pack_011.json`
- `app\src\main\assets\lgs_import\turkce1\lgs_turkce1_pack_012.json`
- `app\src\main\assets\lgs_import\turkce1\lgs_turkce1_pack_013.json`
- `app\src\main\assets\lgs_import\turkce1\lgs_turkce1_pack_014.json`
- `app\src\main\assets\lgs_import\turkce1\lgs_turkce1_pack_015.json`
- `app\src\main\assets\lgs_import\turkce1\lgs_turkce1_pack_016.json`
- `app\src\main\assets\lgs_import\turkce1\lgs_turkce1_pack_017.json`
- `app\src\main\assets\lgs_import\turkce1\lgs_turkce1_pack_018.json`
- `app\src\main\assets\lgs_import\turkce1\lgs_turkce1_pack_019.json`
- `app\src\main\assets\lgs_import\turkce1\lgs_turkce1_pack_020.json`
- `app\src\main\assets\lgs_import\turkce1\lgs_turkce1_pack_021.json`
- `app\src\main\assets\lgs_import\turkce1\lgs_turkce1_pack_022.json`
- `app\src\main\assets\lgs_import\turkce1\lgs_turkce1_pack_023.json`
- `app\src\main\assets\lgs_import\turkce2\lgs_turkce2_pack_029.json`
- `app\src\main\assets\lgs_import\turkce2\lgs_turkce2_pack_030.json`
- `app\src\main\assets\lgs_import\turkce2\lgs_turkce2_pack_031.json`
- `app\src\main\assets\lgs_import\turkce2\lgs_turkce2_pack_032.json`
- `app\src\main\assets\lgs_import\turkce2\lgs_turkce2_pack_033.json`
- `app\src\main\assets\lgs_import\turkce2\lgs_turkce2_pack_034.json`
- `app\src\main\assets\lgs_import\turkce2\lgs_turkce2_pack_035.json`
- `app\src\main\assets\lgs_import\turkce2\lgs_turkce2_pack_036.json`
- `app\src\main\assets\lgs_import\turkce3\lgs_turkce3_pack_040.json`
- `app\src\main\assets\lgs_import\turkce3\lgs_turkce3_pack_041.json`
- `app\src\main\assets\lgs_import\turkce3\lgs_turkce3_pack_042.json`
- `app\src\main\assets\lgs_import\turkce3\lgs_turkce3_pack_043.json`
- `app\src\main\assets\lgs_import\turkce3\lgs_turkce3_pack_044.json`
- `app\src\main\assets\lgs_import\turkce3\lgs_turkce3_pack_045.json`
- `app\src\main\assets\lgs_import\turkce3\lgs_turkce3_pack_046.json`
- `app\src\main\assets\lgs_import\turkce3\lgs_turkce3_pack_047.json`
- `app\src\main\assets\lgs_import\turkce4\lgs_turkce4_pack_043.json`
- `app\src\main\assets\lgs_import\turkce4\lgs_turkce4_pack_044.json`
- `app\src\main\assets\lgs_import\turkce4\lgs_turkce4_pack_045.json`
- `app\src\main\assets\lgs_import\turkce4\lgs_turkce4_pack_046.json`
- `app\src\main\assets\lgs_import\turkce4\lgs_turkce4_pack_047.json`
- `app\src\main\assets\lgs_import\turkce5\lgs_turkce5_pack_003.json`
- `app\src\main\assets\lgs_import\turkce5\lgs_turkce5_pack_004.json`
- `app\src\main\assets\lgs_import\turkce5\lgs_turkce5_pack_007.json`
- `app\src\main\assets\lgs_import\turkce5\lgs_turkce5_pack_008.json`
- `app\src\main\assets\lgs_import\turkce5\lgs_turkce5_pack_011.json`
- `app\src\main\assets\lgs_import\turkce5\lgs_turkce5_pack_012.json`
- `app\src\main\assets\lgs_import\turkce5\lgs_turkce5_pack_014.json`
- `app\src\main\assets\lgs_import\turkce5\lgs_turkce5_pack_016.json`
- `app\src\main\assets\lgs_import\turkce5\lgs_turkce5_pack_018.json`
- `app\src\main\assets\lgs_import\turkce5\lgs_turkce5_pack_020.json`
- `app\src\main\assets\lgs_import\turkce5\lgs_turkce5_pack_022.json`
- `app\src\main\assets\lgs_import\turkce5\lgs_turkce5_pack_024.json`
- `app\src\main\assets\lgs_import\turkce5\lgs_turkce5_pack_026.json`
- `app\src\main\assets\lgs_import\turkce5\lgs_turkce5_pack_028.json`
- `app\src\main\assets\lgs_import\turkce5\lgs_turkce5_pack_030.json`
- `app\src\main\assets\lgs_import\turkce5\lgs_turkce5_pack_032.json`
- `app\src\main\assets\lgs_import\turkce5\lgs_turkce5_pack_034.json`
- `app\src\main\assets\lgs_import\turkce5\lgs_turkce5_pack_035.json`
- `app\src\main\assets\lgs_import\turkce5\lgs_turkce5_pack_038.json`
- `app\src\main\assets\lgs_import\turkce5\lgs_turkce5_pack_039.json`
- `app\src\main\assets\lgs_import\turkce5\lgs_turkce5_pack_042.json`
- `app\src\main\assets\lgs_import\turkce5\lgs_turkce5_pack_043.json`
- `app\src\main\assets\lgs_import\turkce5\lgs_turkce5_pack_046.json`
- `app\src\main\assets\lgs_import\turkce5\lgs_turkce5_pack_047.json`
- `app\src\main\assets\lgs_import\turkce5\lgs_turkce5_pack_050.json`
- `app\src\main\assets\lgs_import\turkce\lgs_turkce_gold_105.json`
- `app\src\main\assets\questions_tr.json`

## By subject (meaningful vs placeholder)

| Subject | Meaningful | Placeholder |
|---------|------------|-------------|
| english | 71 | 0 |
| fen | 297 | 0 |
| hayat1 | 79 | 0 |
| hayat2 | 14 | 0 |
| inkilap | 1 | 0 |
| mat | 159 | 0 |
| sosyal | 27 | 0 |
| turkce | 314 | 0 |

## Subjects improved most

- **turkce**: 314 questions with matching asset
- **fen**: 297 questions with matching asset
- **mat**: 159 questions with matching asset
- **hayat1**: 79 questions with matching asset
- **english**: 71 questions with matching asset
- **sosyal**: 27 questions with matching asset
- **hayat2**: 14 questions with matching asset
- **inkilap**: 1 questions with matching asset

## 10 sample question IDs and final imageAsset paths

| Question ID | imageAsset |
|-------------|------------|
| `lgs_smoke:fen:1` | `quiz_images/fen/picture.png` |
| `lgs_smoke:inkilap:3` | `quiz_images/inkilap/picture.png` |
| `lgs_smoke:mat:1` | `quiz_images/mat/picture.png` |
| `lgs_smoke:mat:4` | `quiz_images/mat/table.png` |
| `lgs_brainbuddy_tr_0004` | `quiz_images/turkce/graph_line.png` |
| `ing1_0001` | `quiz_images/english/picture.png` |
| `eng4_0453` | `quiz_images/english/table.png` |
| `fen_bas_001_q03` | `quiz_images/fen/graph_line.png` |
| `fen_el_003_q07` | `quiz_images/fen/experiment.png` |
| `fen4_0150` | `quiz_images/fen/table.png` |

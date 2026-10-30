// Generat per scripts/genera_avatar.py a partir de scripts/avatar/cataleg.py. No l'editis a mà.
package cat.andana947.avatar

import cat.andana947.R

internal object CatalegGenerat {
    val cos: List<Capa> = listOf(
        Capa(10, "M87 110 L113 110 L114 170 L86 170 Z", "pell", "tinta", 3.0f),
        Capa(11, "M87 124 C95 132 105 132 113 124 L113 136 C105 140 95 140 87 136 Z", "pell_ombra", null, 3.0f),
        Capa(38, "M64 84 C67.8661 84 71 88.477 71 94 C71 99.523 67.8661 104 64 104 C60.1339 104 57 99.523 57 94 C57 88.477 60.1339 84 64 84 Z", "pell", "tinta", 3.0f),
        Capa(39, "M65 88 Q60 94 65 100", null, "tinta", 2f),
        Capa(38, "M136 84 C132.134 84 129 88.477 129 94 C129 99.523 132.134 104 136 104 C139.866 104 143 99.523 143 94 C143 88.477 139.866 84 136 84 Z", "pell", "tinta", 3.0f),
        Capa(39, "M135 88 Q140 94 135 100", null, "tinta", 2f),
        Capa(40, "M100 48 C119.883 48 136 65.908 136 88 C136 110.092 119.883 128 100 128 C80.1172 128 64 110.092 64 88 C64 65.908 80.1172 48 100 48 Z", "pell", "tinta", 3.0f),
    )

    val peces: List<Peca> = listOf(
        Peca("cara_noi", "cara", null, true, listOf(
            Capa(50, "M86 89 C88.2092 89 90 91.2385 90 94 C90 96.7615 88.2092 99 86 99 C83.7908 99 82 96.7615 82 94 C82 91.2385 83.7908 89 86 89 Z", "tinta", null, 3.0f),
            Capa(50, "M114 89 C111.791 89 110 91.2385 110 94 C110 96.7615 111.791 99 114 99 C116.209 99 118 96.7615 118 94 C118 91.2385 116.209 89 114 89 Z", "tinta", null, 3.0f),
            Capa(50, "M77 83 Q86 78 95 83", null, "tinta", 3f),
            Capa(50, "M123 83 Q114 78 105 83", null, "tinta", 3f),
            Capa(50, "M100 98 Q104 104 99 107", null, "tinta", 2.5f),
            Capa(50, "M89 113 Q100 122 111 113", null, "tinta", 3f),
            Capa(49, "M78 103.5 C81.3138 103.5 84 105.067 84 107 C84 108.933 81.3138 110.5 78 110.5 C74.6862 110.5 72 108.933 72 107 C72 105.067 74.6862 103.5 78 103.5 Z", "#FF9CB8", null, 3.0f),
            Capa(49, "M122 103.5 C118.686 103.5 116 105.067 116 107 C116 108.933 118.686 110.5 122 110.5 C125.314 110.5 128 108.933 128 107 C128 105.067 125.314 103.5 122 103.5 Z", "#FF9CB8", null, 3.0f),
        )),
        Peca("cara_noia", "cara", null, true, listOf(
            Capa(50, "M86 89 C88.2092 89 90 91.2385 90 94 C90 96.7615 88.2092 99 86 99 C83.7908 99 82 96.7615 82 94 C82 91.2385 83.7908 89 86 89 Z", "tinta", null, 3.0f),
            Capa(50, "M114 89 C111.791 89 110 91.2385 110 94 C110 96.7615 111.791 99 114 99 C116.209 99 118 96.7615 118 94 C118 91.2385 116.209 89 114 89 Z", "tinta", null, 3.0f),
            Capa(50, "M77 83 Q86 78 95 83", null, "tinta", 3f),
            Capa(50, "M123 83 Q114 78 105 83", null, "tinta", 3f),
            Capa(50, "M100 98 Q104 104 99 107", null, "tinta", 2.5f),
            Capa(50, "M89 113 Q100 122 111 113", null, "tinta", 3f),
            Capa(49, "M78 103.5 C81.3138 103.5 84 105.067 84 107 C84 108.933 81.3138 110.5 78 110.5 C74.6862 110.5 72 108.933 72 107 C72 105.067 74.6862 103.5 78 103.5 Z", "#FF9CB8", null, 3.0f),
            Capa(49, "M122 103.5 C118.686 103.5 116 105.067 116 107 C116 108.933 118.686 110.5 122 110.5 C125.314 110.5 128 108.933 128 107 C128 105.067 125.314 103.5 122 103.5 Z", "#FF9CB8", null, 3.0f),
            Capa(50, "M82 90 L77 86", null, "tinta", 2.5f),
            Capa(50, "M118 90 L123 86", null, "tinta", 2.5f),
        )),
        Peca("cara_director", "cara", null, true, listOf(
            Capa(50, "M80 96 Q86 89 92 96", null, "tinta", 3f),
            Capa(50, "M120 96 Q114 89 108 96", null, "tinta", 3f),
            Capa(50, "M100 98 Q104 104 99 107", null, "tinta", 2.5f),
            Capa(50, "M92 118 Q100 124 108 118", null, "tinta", 3f),
            Capa(49, "M78 103.5 C81.3138 103.5 84 105.067 84 107 C84 108.933 81.3138 110.5 78 110.5 C74.6862 110.5 72 108.933 72 107 C72 105.067 74.6862 103.5 78 103.5 Z", "#FF9CB8", null, 3.0f),
            Capa(49, "M122 103.5 C118.686 103.5 116 105.067 116 107 C116 108.933 118.686 110.5 122 110.5 C125.314 110.5 128 108.933 128 107 C128 105.067 125.314 103.5 122 103.5 Z", "#FF9CB8", null, 3.0f),
        )),
        Peca("cara_director_ullet", "cara", null, true, listOf(
            Capa(50, "M80 96 Q86 89 92 96", null, "tinta", 3f),
            Capa(50, "M108 95 Q114 99 120 95", null, "tinta", 3f),
            Capa(50, "M100 98 Q104 104 99 107", null, "tinta", 2.5f),
            Capa(50, "M92 118 Q100 124 108 118", null, "tinta", 3f),
            Capa(49, "M78 103.5 C81.3138 103.5 84 105.067 84 107 C84 108.933 81.3138 110.5 78 110.5 C74.6862 110.5 72 108.933 72 107 C72 105.067 74.6862 103.5 78 103.5 Z", "#FF9CB8", null, 3.0f),
            Capa(49, "M122 103.5 C118.686 103.5 116 105.067 116 107 C116 108.933 118.686 110.5 122 110.5 C125.314 110.5 128 108.933 128 107 C128 105.067 125.314 103.5 122 103.5 Z", "#FF9CB8", null, 3.0f),
        )),
        Peca("cabell_curt", "cabell", R.string.avatar_cabell_curt, false, listOf(
            Capa(70, "M62 92 C58 58 78 42 102 42 C126 42 144 58 138 92 C136 80 130 70 120 65 C108 72 88 73 76 65 C68 72 64 80 62 92 Z", "cabell", "tinta", 3.0f),
        )),
        Peca("cabell_rapat", "cabell", R.string.avatar_cabell_rapat, false, listOf(
            Capa(70, "M65 80 C67 56 84 47 100 47 C116 47 133 56 135 80 C120 69 80 69 65 80 Z", "cabell", "tinta", 2f),
        )),
        Peca("cabell_arrissat", "cabell", R.string.avatar_cabell_arrissat, false, listOf(
            Capa(70, "M60 98 Q48 86 57 73 Q53 57 69 51 Q75 36 92 39 Q101 29 113 37 Q129 35 133 51 Q149 57 145 73 Q153 86 140 98 Q138 80 126 71 Q113 78 100 71 Q87 78 74 71 Q62 80 60 98 Z", "cabell", "tinta", 3.0f),
        )),
        Peca("cabell_llarg", "cabell", R.string.avatar_cabell_llarg, false, listOf(
            Capa(5, "M60 90 C56 52 80 38 100 38 C120 38 144 52 140 90 L146 158 C128 166 72 166 54 158 Z", "cabell_ombra", "tinta", 3.0f),
            Capa(70, "M62 94 C58 56 80 42 100 42 C122 42 142 56 138 94 C134 74 124 63 112 59 C104 70 86 77 67 77 C64 83 63 88 62 94 Z", "cabell", "tinta", 3.0f),
        )),
        Peca("cabell_cua", "cabell", R.string.avatar_cabell_cua, false, listOf(
            Capa(5, "M128 58 C152 54 162 82 154 112 C150 126 141 134 136 130 C143 110 141 84 128 72 Z", "cabell_ombra", "tinta", 3.0f),
            Capa(6, "M127 58 L135 56 L138 66 L130 70 Z", "#FF3D7F", "tinta", 2f),
            Capa(70, "M62 90 C60 56 80 42 100 42 C120 42 140 56 138 90 C132 70 116 60 100 60 C84 60 68 70 62 90 Z", "cabell", "tinta", 3.0f),
        )),
        Peca("cabell_monyo", "cabell", R.string.avatar_cabell_monyo, false, listOf(
            Capa(5, "M100 23 C108.837 23 116 29.2678 116 37 C116 44.7322 108.837 51 100 51 C91.1632 51 84 44.7322 84 37 C84 29.2678 91.1632 23 100 23 Z", "cabell_ombra", "tinta", 3.0f),
            Capa(70, "M62 90 C60 56 80 42 100 42 C120 42 140 56 138 90 C132 70 116 60 100 60 C84 60 68 70 62 90 Z", "cabell", "tinta", 3.0f),
        )),
        Peca("cabell_calb", "cabell", null, true, listOf(
            Capa(70, "M63 100 C58 84 62 70 72 63 C70 76 70 88 69 101 Z", "cabell", "tinta", 2f),
            Capa(70, "M137 100 C142 84 138 70 128 63 C130 76 130 88 131 101 Z", "cabell", "tinta", 2f),
        )),
        Peca("cap_revisor", "cap", R.string.avatar_cap_revisor, false, listOf(
            Capa(80, "M60 74 C60 46 80 34 100 34 C120 34 140 46 140 74 Z", "#23304A", "tinta", 3.0f),
            Capa(81, "M60 64 L140 64 L140 78 L60 78 Z", "#111111", "tinta", 3.0f),
            Capa(82, "M57 78 L143 78 C138 91 62 91 57 78 Z", "#111111", "tinta", 3.0f),
            Capa(82, "M100 46 C103.866 46 107 48.6862 107 52 C107 55.3138 103.866 58 100 58 C96.1339 58 93 55.3138 93 52 C93 48.6862 96.1339 46 100 46 Z", "#FF3D7F", "tinta", 2f),
        )),
        Peca("cap_beisbol", "cap", R.string.avatar_cap_beisbol, false, listOf(
            Capa(80, "M62 78 C60 46 80 35 100 35 C120 35 140 46 138 78 Z", "#FF3D7F", "tinta", 3.0f),
            Capa(81, "M100 36 L100 76", null, "tinta", 2f),
            Capa(82, "M58 78 C80 70 120 70 142 78 C137 91 63 91 58 78 Z", "#111111", "tinta", 3.0f),
            Capa(82, "M100 32 C102.209 32 104 33.3431 104 35 C104 36.6569 102.209 38 100 38 C97.7908 38 96 36.6569 96 35 C96 33.3431 97.7908 32 100 32 Z", "#111111", null, 3.0f),
        )),
        Peca("cap_llana", "cap", R.string.avatar_cap_llana, false, listOf(
            Capa(79, "M100 20 C106.075 20 111 24.477 111 30 C111 35.523 106.075 40 100 40 C93.9247 40 89 35.523 89 30 C89 24.477 93.9247 20 100 20 Z", "#F2F2F2", "tinta", 3.0f),
            Capa(80, "M62 78 C60 46 80 34 100 34 C120 34 140 46 138 78 Z", "#E0A526", "tinta", 3.0f),
            Capa(81, "M59 68 L141 68 L142 84 L58 84 Z", "#C48A12", "tinta", 3.0f),
            Capa(81, "M72 70 L72 82 M86 70 L86 82 M100 70 L100 82 M114 70 L114 82 M128 70 L128 82", null, "tinta", 2f),
        )),
        Peca("cap_casc", "cap", R.string.avatar_cap_casc, false, listOf(
            Capa(80, "M57 84 C55 44 80 30 100 30 C120 30 145 44 143 84 C130 75 70 75 57 84 Z", "#2EB8A8", "tinta", 3.0f),
            Capa(81, "M88 34 L86 54 M100 32 L100 52 M112 34 L114 54", null, "tinta", 3f),
            Capa(81, "M62 84 L80 124 M138 84 L120 124", null, "tinta", 2f),
        )),
        Peca("cap_barretina", "cap", R.string.avatar_cap_barretina, false, listOf(
            Capa(80, "M64 72 C66 40 92 28 114 31 C138 34 152 50 156 70 C158 82 148 85 143 76 C139 62 131 59 127 68 Z", "#C62D1F", "tinta", 3.0f),
            Capa(81, "M61 66 C80 60 120 60 139 66 L139 80 C120 74 80 74 61 80 Z", "#8E1F15", "tinta", 3.0f),
        )),
        Peca("cap_cap_estacio", "cap", null, true, listOf(
            Capa(80, "M60 74 C60 46 80 34 100 34 C120 34 140 46 140 74 Z", "#C62D1F", "tinta", 3.0f),
            Capa(81, "M60 64 L140 64 L140 78 L60 78 Z", "#111111", "tinta", 3.0f),
            Capa(81, "M62 70 L138 70", null, "#E0A526", 3f),
            Capa(82, "M57 78 L143 78 C138 91 62 91 57 78 Z", "#111111", "tinta", 3.0f),
            Capa(82, "M100 46 C103.866 46 107 48.6862 107 52 C107 55.3138 103.866 58 100 58 C96.1339 58 93 55.3138 93 52 C93 48.6862 96.1339 46 100 46 Z", "#E0A526", "tinta", 2f),
        )),
        Peca("tronc_samarreta", "tronc", R.string.avatar_tronc_samarreta, false, listOf(
            Capa(20, "M24 200 C24 170 44 152 78 148 L88 148 L100 166 L112 148 L122 148 C156 152 176 170 176 200 Z", "#FFFFFF", "tinta", 3.0f),
        )),
        Peca("tronc_texana", "tronc", R.string.avatar_tronc_texana, false, listOf(
            Capa(20, "M24 200 C24 170 44 152 78 148 C86 156 114 156 122 148 C156 152 176 170 176 200 Z", "#FFFFFF", "tinta", 3.0f),
            Capa(21, "M24 200 C24 170 44 152 78 148 L90 150 L97 200 Z", "#4A78B5", "tinta", 3.0f),
            Capa(21, "M176 200 C176 170 156 152 122 148 L110 150 L103 200 Z", "#4A78B5", "tinta", 3.0f),
            Capa(22, "M78 148 L90 150 L95 176 L80 162 Z", "#3A6299", "tinta", 2.5f),
            Capa(22, "M122 148 L110 150 L105 176 L120 162 Z", "#3A6299", "tinta", 2.5f),
            Capa(23, "M93 179.5 C94.3808 179.5 95.5 180.619 95.5 182 C95.5 183.381 94.3808 184.5 93 184.5 C91.6192 184.5 90.5 183.381 90.5 182 C90.5 180.619 91.6192 179.5 93 179.5 Z", "#E0A526", null, 3.0f),
            Capa(23, "M107 179.5 C108.381 179.5 109.5 180.619 109.5 182 C109.5 183.381 108.381 184.5 107 184.5 C105.619 184.5 104.5 183.381 104.5 182 C104.5 180.619 105.619 179.5 107 179.5 Z", "#E0A526", null, 3.0f),
            Capa(23, "M44 172 L58 168 M156 172 L142 168", null, "tinta", 2f),
        )),
        Peca("tronc_armilla", "tronc", R.string.avatar_tronc_armilla, false, listOf(
            Capa(20, "M24 200 C24 170 44 152 78 148 L88 148 L100 166 L112 148 L122 148 C156 152 176 170 176 200 Z", "#9AA0A6", "tinta", 3.0f),
            Capa(21, "M30 200 C34 172 50 158 80 150 L93 200 Z", "#E6F23A", "tinta", 3.0f),
            Capa(21, "M170 200 C166 172 150 158 120 150 L107 200 Z", "#E6F23A", "tinta", 3.0f),
            Capa(22, "M37 176 L88 176 L90 186 L33 186 Z", "#D9D9D9", "tinta", 2f),
            Capa(22, "M163 176 L112 176 L110 186 L167 186 Z", "#D9D9D9", "tinta", 2f),
        )),
        Peca("tronc_abric", "tronc", R.string.avatar_tronc_abric, false, listOf(
            Capa(20, "M24 200 C24 170 44 152 78 148 L88 148 L100 166 L112 148 L122 148 C156 152 176 170 176 200 Z", "#FFFFFF", "tinta", 3.0f),
            Capa(21, "M24 200 C24 170 44 152 78 148 L90 150 L97 200 Z", "#3E4349", "tinta", 3.0f),
            Capa(21, "M176 200 C176 170 156 152 122 148 L110 150 L103 200 Z", "#3E4349", "tinta", 3.0f),
            Capa(22, "M78 148 L90 150 L95 176 L80 162 Z", "#2A2E33", "tinta", 2.5f),
            Capa(22, "M122 148 L110 150 L105 176 L120 162 Z", "#2A2E33", "tinta", 2.5f),
        )),
        Peca("tronc_dessuadora", "tronc", R.string.avatar_tronc_dessuadora, false, listOf(
            Capa(5, "M66 156 C60 136 72 124 86 130 L114 130 C128 124 140 136 134 156 Z", "#D92E6A", "tinta", 3.0f),
            Capa(20, "M24 200 C24 170 44 152 78 148 C86 156 114 156 122 148 C156 152 176 170 176 200 Z", "#FF3D7F", "tinta", 3.0f),
            Capa(21, "M92 154 L90 176 M108 154 L110 176", null, "tinta", 2.5f),
            Capa(21, "M74 186 L126 186 L130 200 L70 200 Z", "#D92E6A", "tinta", 2.5f),
        )),
        Peca("tronc_uniforme", "tronc", R.string.avatar_tronc_uniforme, false, listOf(
            Capa(20, "M24 200 C24 170 44 152 78 148 L88 148 L100 166 L112 148 L122 148 C156 152 176 170 176 200 Z", "#FFFFFF", "tinta", 3.0f),
            Capa(21, "M24 200 C24 170 44 152 78 148 L90 150 L97 200 Z", "#23304A", "tinta", 3.0f),
            Capa(21, "M176 200 C176 170 156 152 122 148 L110 150 L103 200 Z", "#23304A", "tinta", 3.0f),
            Capa(22, "M78 148 L90 150 L95 176 L80 162 Z", "#17203A", "tinta", 2.5f),
            Capa(22, "M122 148 L110 150 L105 176 L120 162 Z", "#17203A", "tinta", 2.5f),
            Capa(23, "M96 151 L104 151 L106 158 L102 184 L100 188 L98 184 L94 158 Z", "#FF3D7F", "tinta", 2f),
            Capa(23, "M56 166 L72 166 L72 174 L56 174 Z", "#E0A526", "tinta", 2f),
        )),
        Peca("coll_bufanda", "coll", R.string.avatar_coll_bufanda, false, listOf(
            Capa(30, "M78 140 C90 151 110 151 122 140 L126 157 C111 168 89 168 74 157 Z", "#FF3D7F", "tinta", 3.0f),
            Capa(31, "M104 156 L120 155 L118 192 L102 192 Z", "#FF3D7F", "tinta", 3.0f),
            Capa(32, "M104 168 L119 168 M103 180 L118 180", null, "#FFFFFF", 4f),
        )),
        Peca("ulls_rodones", "ulls", R.string.avatar_ulls_rodones, false, listOf(
            Capa(60, "M86 84 C92.0753 84 97 88.477 97 94 C97 99.523 92.0753 104 86 104 C79.9247 104 75 99.523 75 94 C75 88.477 79.9247 84 86 84 Z", null, "tinta", 3f),
            Capa(60, "M114 84 C120.075 84 125 88.477 125 94 C125 99.523 120.075 104 114 104 C107.925 104 103 99.523 103 94 C103 88.477 107.925 84 114 84 Z", null, "tinta", 3f),
            Capa(60, "M97 93 Q100 89 103 93", null, "tinta", 3f),
            Capa(60, "M75 91 L65 89 M125 91 L135 89", null, "tinta", 3f),
        )),
        Peca("ulls_sol", "ulls", R.string.avatar_ulls_sol, false, listOf(
            Capa(60, "M72 87 L98 87 L96 101 C94 107 76 107 74 101 Z", "#111111", "tinta", 3.0f),
            Capa(60, "M128 87 L102 87 L104 101 C106 107 124 107 126 101 Z", "#111111", "tinta", 3.0f),
            Capa(60, "M98 90 L102 90", null, "tinta", 3f),
            Capa(61, "M77 91 L83 91", null, "#FFFFFF", 2f),
            Capa(61, "M105 91 L111 91", null, "#FFFFFF", 2f),
        )),
        Peca("orelles_auriculars", "orelles", R.string.avatar_orelles_auriculars, false, listOf(
            Capa(90, "M60 94 C54 36 146 36 140 94", null, "tinta", 7f),
            Capa(90, "M61 83 C65.9707 83 70 88.8201 70 96 C70 103.18 65.9707 109 61 109 C56.0293 109 52 103.18 52 96 C52 88.8201 56.0293 83 61 83 Z", "#FF3D7F", "tinta", 3.0f),
            Capa(90, "M139 83 C143.971 83 148 88.8201 148 96 C148 103.18 143.971 109 139 109 C134.029 109 130 103.18 130 96 C130 88.8201 134.029 83 139 83 Z", "#FF3D7F", "tinta", 3.0f),
        )),
        Peca("bigoti", "bigoti", null, true, listOf(
            Capa(55, "M78 109 C84 101 95 101 100 106 C105 101 116 101 122 109 C117 116 108 116 100 111 C92 116 83 116 78 109 Z", "cabell", "tinta", 2f),
        )),
    )

    val tonsPell: List<ColorAvatar> = listOf(
        ColorAvatar("pell_1", 0xFFF8DCC8, R.string.avatar_pell_1),
        ColorAvatar("pell_2", 0xFFEEC1A0, R.string.avatar_pell_2),
        ColorAvatar("pell_3", 0xFFD29B74, R.string.avatar_pell_3),
        ColorAvatar("pell_4", 0xFFA86F4C, R.string.avatar_pell_4),
        ColorAvatar("pell_5", 0xFF6E4630, R.string.avatar_pell_5),
    )

    val colorsCabell: List<ColorAvatar> = listOf(
        ColorAvatar("cabell_negre", 0xFF2B2B2B, R.string.avatar_cabell_negre),
        ColorAvatar("cabell_castany", 0xFF6B4226, R.string.avatar_cabell_castany),
        ColorAvatar("cabell_ros", 0xFFE3B95B, R.string.avatar_cabell_ros),
        ColorAvatar("cabell_pelroig", 0xFFB5522B, R.string.avatar_cabell_pelroig),
        ColorAvatar("cabell_gris", 0xFFB4B4B4, R.string.avatar_cabell_gris),
        ColorAvatar("cabell_magenta", 0xFFFF3D7F, R.string.avatar_cabell_magenta),
    )

    val ranures: List<Ranura> = listOf(
        Ranura("cabell", true, R.string.avatar_ranura_cabell),
        Ranura("cap", false, R.string.avatar_ranura_cap),
        Ranura("tronc", true, R.string.avatar_ranura_tronc),
        Ranura("coll", false, R.string.avatar_ranura_coll),
        Ranura("ulls", false, R.string.avatar_ranura_ulls),
        Ranura("orelles", false, R.string.avatar_ranura_orelles),
    )

    val director = Avatar(cos = "noi", pell = "pell_2", cabell = "cabell_calb", colorCabell = "cabell_gris", cap = "cap_cap_estacio", tronc = "tronc_uniforme", ulls = "ulls_rodones", bigoti = "bigoti")

    val inicial = Avatar(cos = "noi", pell = "pell_2", cabell = "cabell_curt", colorCabell = "cabell_castany", tronc = "tronc_samarreta")
}

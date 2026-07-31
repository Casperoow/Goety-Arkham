import fs from "node:fs";
import path from "node:path";
import crypto from "node:crypto";
import zlib from "node:zlib";

// LEGACY TOOL: young_deep_one.bbmodel is now the only editable art source.
// This procedural generator predates the artist-approved Blockbench model and
// must never be used by builds or routine modeling work.
const legacyOverwriteFlag = "--allow-legacy-young-deep-one-overwrite";
if (!process.argv.includes(legacyOverwriteFlag)) {
    throw new Error(
        "Legacy young_deep_one generator is disabled because it overwrites "
        + "the artist-approved .bbmodel, .geo.json, texture, and previews. "
        + `Pass ${legacyOverwriteFlag} only when intentionally recovering `
        + "the obsolete generated version."
    );
}

const workspace = path.resolve(import.meta.dirname, "..", "..");
const geometryPath = path.join(
    workspace,
    "src",
    "main",
    "resources",
    "assets",
    "goetyarkham",
    "geo",
    "young_deep_one.geo.json"
);
const texturePath = path.join(
    workspace,
    "src",
    "main",
    "resources",
    "assets",
    "goetyarkham",
    "textures",
    "entity",
    "young_deep_one.png"
);
const uvGuidePath = path.join(
    workspace,
    "docs",
    "modeling",
    "young_deep_one_uv_layout.svg"
);
const previewPath = path.join(
    workspace,
    "docs",
    "modeling",
    "young_deep_one_preview.png"
);
const previewPaths = {
    front: path.join(
        workspace,
        "docs",
        "modeling",
        "young_deep_one_front.png"
    ),
    right: path.join(
        workspace,
        "docs",
        "modeling",
        "young_deep_one_right.png"
    ),
    back: path.join(
        workspace,
        "docs",
        "modeling",
        "young_deep_one_back.png"
    ),
    leftFront: path.join(
        workspace,
        "docs",
        "modeling",
        "young_deep_one_left_front_45.png"
    ),
    lowAngle: path.join(
        workspace,
        "docs",
        "modeling",
        "young_deep_one_low_angle.png"
    )
};
const bbmodelPath = path.join(
    workspace,
    "docs",
    "modeling",
    "young_deep_one.bbmodel"
);

const textureWidth = 128;
const textureHeight = 128;

const materialColours = {
    skin: [35, 48, 31, 255],
    skin_light: [62, 73, 43, 255],
    skin_dark: [22, 30, 23, 255],
    rag: [163, 170, 157, 255],
    rag_dark: [112, 121, 109, 255],
    webbing: [47, 59, 42, 255],
    claw: [39, 43, 38, 255],
    eye: [255, 235, 35, 255]
};

const bones = [];

function cube(name, origin, size, material, options = {}) {
    return {
        name,
        origin,
        size,
        material,
        rotation: options.rotation,
        pivot: options.pivot,
        inflate: options.inflate,
        mirror: options.mirror
    };
}

function bone(name, parent, pivot, cubes = []) {
    bones.push({
        name,
        parent,
        pivot,
        cubes
    });
}

bone("young_deep_one", undefined, [0, 0, 0]);
bone("body", "young_deep_one", [0, 5.5, 0]);

bone("pelvis", "body", [0, 5.2, 2], [
    cube(
        "pelvis_core",
        [-4, 1.8, -0.5],
        [8, 5, 7],
        "skin",
        {rotation: [-8, 0, 0], pivot: [0, 4.5, 3]}
    ),
    cube(
        "pelvis_front",
        [-3.2, 1.7, -3.1],
        [6.4, 4, 4.2],
        "skin_light",
        {rotation: [-12, 0, 0], pivot: [0, 3.9, -0.2]}
    )
]);

bone("left_upper_leg", "pelvis", [3.5, 5.3, 2.2], [
    cube(
        "left_thigh",
        [2, 0.7, 0.7],
        [3.8, 6.5, 3.5],
        "skin",
        {rotation: [-50, 0, 25], pivot: [3.5, 5.3, 2.2]}
    ),
    cube(
        "left_knee_mass",
        [3.6, 0.8, 4.5],
        [3.6, 3.2, 3.4],
        "skin_light",
        {rotation: [-10, 0, 15], pivot: [5.4, 2.5, 6.1]}
    )
]);

bone("left_lower_leg", "left_upper_leg", [5.4, 2.5, 6.1], [
    cube(
        "left_shin",
        [3.75, -0.5, 4.6],
        [3.3, 5.2, 3],
        "skin",
        {rotation: [75, 0, -3], pivot: [5.4, 2.5, 6.1]}
    ),
    cube(
        "left_ankle",
        [3.65, 0.3, 0.3],
        [3.1, 2.8, 3],
        "skin_dark",
        {rotation: [8, 0, 2], pivot: [5.2, 1.4, 1.8]}
    )
]);

bone("left_foot", "left_lower_leg", [5.2, 1.1, 1.5], [
    cube("left_sole", [2.8, 0, -2.1], [4.8, 1.5, 5.5], "skin_dark"),
    cube("left_toe_outer", [2.85, 0.35, -5.6], [0.8, 0.8, 3.7], "claw"),
    cube("left_toe_mid_outer", [3.95, 0.35, -5.9], [0.8, 0.8, 4], "claw"),
    cube("left_toe_mid_inner", [5.15, 0.35, -5.8], [0.8, 0.8, 3.9], "claw"),
    cube("left_toe_inner", [6.35, 0.35, -5.3], [0.8, 0.8, 3.4], "claw"),
    cube("left_foot_web_outer", [3.58, 0.42, -4.15], [0.48, 0.28, 2.25], "webbing"),
    cube("left_foot_web_mid", [4.75, 0.42, -4.35], [0.48, 0.28, 2.45], "webbing"),
    cube("left_foot_web_inner", [5.92, 0.42, -4], [0.48, 0.28, 2.1], "webbing")
]);

bone("right_upper_leg", "pelvis", [-3.5, 5.3, 2.2], [
    cube(
        "right_thigh",
        [-5.8, 0.7, 0.7],
        [3.8, 6.3, 3.5],
        "skin",
        {rotation: [-50, 0, -25], pivot: [-3.5, 5.3, 2.2]}
    ),
    cube(
        "right_knee_mass",
        [-7.2, 0.8, 4.5],
        [3.6, 3.3, 3.4],
        "skin_light",
        {rotation: [-10, 0, -15], pivot: [-5.4, 2.5, 6.1]}
    )
]);

bone("right_lower_leg", "right_upper_leg", [-5.4, 2.5, 6.1], [
    cube(
        "right_shin",
        [-7.05, -0.5, 4.6],
        [3.3, 5.2, 3],
        "skin",
        {rotation: [75, 0, 3], pivot: [-5.4, 2.5, 6.1]}
    ),
    cube(
        "right_ankle",
        [-6.75, 0.3, 0.3],
        [3.1, 2.8, 3],
        "skin_dark",
        {rotation: [8, 0, -2], pivot: [-5.2, 1.4, 1.8]}
    )
]);

bone("right_foot", "right_lower_leg", [-5.2, 1.1, 1.5], [
    cube("right_sole", [-7.6, 0, -2.2], [4.8, 1.5, 5.5], "skin_dark"),
    cube("right_toe_outer", [-3.65, 0.35, -5.5], [0.8, 0.8, 3.5], "claw"),
    cube("right_toe_mid_outer", [-4.8, 0.35, -5.85], [0.8, 0.8, 3.85], "claw"),
    cube("right_toe_mid_inner", [-6, 0.35, -6], [0.8, 0.8, 4], "claw"),
    cube("right_toe_inner", [-7.15, 0.35, -5.45], [0.8, 0.8, 3.45], "claw"),
    cube("right_foot_web_outer", [-4.02, 0.42, -4.05], [0.48, 0.28, 2.05], "webbing"),
    cube("right_foot_web_mid", [-5.2, 0.42, -4.4], [0.48, 0.28, 2.4], "webbing"),
    cube("right_foot_web_inner", [-6.38, 0.42, -4.1], [0.48, 0.28, 2.1], "webbing")
]);

bone("lower_torso", "body", [0, 4.8, -0.5], [
    cube(
        "abdomen",
        [-3.6, 1.8, -4],
        [7.2, 6, 7],
        "skin",
        {rotation: [-8, 0, 0], pivot: [0, 4.8, -0.5]}
    ),
    cube(
        "belly",
        [-3, 1.8, -5.6],
        [6, 4.2, 4.5],
        "skin_light",
        {rotation: [-10, 0, 0], pivot: [0, 3.8, -3.1]}
    )
]);

bone("upper_torso", "lower_torso", [0, 5.3, -3.8], [
    cube(
        "chest",
        [-5, 2, -7.2],
        [10, 7, 8.2],
        "skin",
        {rotation: [-10, 0, 0], pivot: [0, 5.3, -3.5]}
    ),
    cube(
        "upper_back_hump",
        [-4.2, 5.8, -3.8],
        [8.4, 4, 5.6],
        "skin_dark",
        {rotation: [-8, 0, 0], pivot: [0, 7, -1]}
    ),
    cube(
        "left_shoulder_mass",
        [3.5, 4, -6.9],
        [3, 4.2, 5],
        "skin_light",
        {rotation: [-10, 0, 12], pivot: [4.7, 6.2, -4.5]}
    ),
    cube(
        "right_shoulder_mass",
        [-6.5, 4, -6.9],
        [3, 4.2, 5],
        "skin_light",
        {rotation: [-10, 0, -12], pivot: [-4.7, 6.2, -4.5]}
    )
]);

bone("neck", "upper_torso", [0, 8, -7], [
    cube(
        "neck_core",
        [-2.9, 8.4, -10.1],
        [5.8, 7.1, 6],
        "skin",
        {rotation: [-25, 0, 0], pivot: [0, 10, -7]}
    ),
    cube(
        "neck_front",
        [-2.25, 9.5, -12],
        [4.5, 6.2, 4],
        "skin_dark",
        {rotation: [-25, 0, 0], pivot: [0, 11, -9.8]}
    )
]);

bone("head", "neck", [0, 17.7166, -12], [
    cube(
        "head_main",
        [-3.2, 17.3666, -15.2],
        [6.4, 6, 6.2],
        "skin_dark",
        {rotation: [-18, 0, 0], pivot: [0, 17.3666, -12]}
    ),
    cube(
        "head_top",
        [-2.6, 22.3666, -14.6],
        [5.2, 1, 5.2],
        "skin",
        {rotation: [-18, 0, 0], pivot: [0, 17.3666, -12]}
    ),
    cube(
        "face_lower",
        [-2.6, 15.8666, -15.45],
        [5.2, 4.5, 5.1],
        "skin_dark",
        {rotation: [-18, 0, 0], pivot: [0, 17.3666, -12]}
    ),
    cube(
        "head_left_rounding",
        [2.65, 18.3666, -14.55],
        [0.8, 3.8, 4.7],
        "skin",
        {rotation: [-18, 0, 0], pivot: [0, 17.3666, -12]}
    ),
    cube(
        "head_right_rounding",
        [-3.45, 18.3666, -14.55],
        [0.8, 3.8, 4.7],
        "skin",
        {rotation: [-18, 0, 0], pivot: [0, 17.3666, -12]}
    )
]);

bone("eyes", "head", [0, 20.9166, -15.3], [
    cube(
        "left_eye",
        [0.75, 19.8666, -15.55],
        [1.5, 1.5, 0.5],
        "eye",
        {rotation: [-18, 0, 0], pivot: [0, 17.3666, -12]}
    ),
    cube(
        "right_eye",
        [-2.25, 19.8666, -15.55],
        [1.5, 1.5, 0.5],
        "eye",
        {rotation: [-18, 0, 0], pivot: [0, 17.3666, -12]}
    )
]);

bone("left_upper_arm", "upper_torso", [4.8, 6.8, -5], [
    cube(
        "left_upper_arm_core",
        [3.3, -1.2, -6.5],
        [3, 8.5, 3],
        "skin",
        {rotation: [36, 0, 38], pivot: [4.8, 6.8, -5]}
    ),
    cube(
        "left_elbow_mass",
        [6, 1.8, -10.1],
        [3.2, 3.2, 3.2],
        "skin_light",
        {rotation: [20, 0, 22], pivot: [7.6, 3.4, -8.5]}
    )
]);

bone("left_forearm", "left_upper_arm", [7.6, 3.4, -8.5], [
    cube(
        "left_forearm_core",
        [6.2, -1, -9.95],
        [2.8, 7.3, 2.9],
        "skin",
        {rotation: [72, 0, -10], pivot: [7.6, 3.4, -8.5]}
    ),
    cube(
        "left_wrist_mass",
        [5.65, 0.4, -15.1],
        [3.1, 2.5, 3],
        "skin_dark",
        {rotation: [8, 0, 3], pivot: [7.2, 1.4, -13.6]}
    )
]);

bone("left_hand", "left_forearm", [7.2, 1.3, -13.8], [
    cube("left_palm", [5.3, 0.5, -15.7], [4, 2, 4], "skin_dark"),
    cube("left_finger_outer", [8.15, 0.35, -20], [0.75, 0.75, 4.5], "claw"),
    cube("left_finger_mid_outer", [7.05, 0.35, -20.35], [0.75, 0.75, 4.85], "claw"),
    cube("left_finger_mid_inner", [5.95, 0.35, -20.15], [0.75, 0.75, 4.65], "claw"),
    cube("left_finger_inner_short", [4.85, 0.4, -18.9], [0.75, 0.75, 3.4], "claw"),
    cube("left_hand_web_outer", [7.75, 0.42, -18.15], [0.48, 0.28, 2.65], "webbing"),
    cube("left_hand_web_mid", [6.65, 0.42, -18.4], [0.48, 0.28, 2.9], "webbing"),
    cube("left_hand_web_inner", [5.55, 0.42, -17.75], [0.48, 0.28, 2.25], "webbing")
]);

bone("right_upper_arm", "upper_torso", [-4.8, 6.8, -5], [
    cube(
        "right_upper_arm_core",
        [-6.3, -1.2, -6.5],
        [3, 8.7, 3],
        "skin",
        {rotation: [36, 0, -38], pivot: [-4.8, 6.8, -5]}
    ),
    cube(
        "right_elbow_mass",
        [-9.2, 1.7, -10.1],
        [3.2, 3.3, 3.2],
        "skin_light",
        {rotation: [20, 0, -22], pivot: [-7.6, 3.4, -8.5]}
    )
]);

bone("right_forearm", "right_upper_arm", [-7.6, 3.4, -8.5], [
    cube(
        "right_forearm_core",
        [-9, -1, -9.95],
        [2.8, 7.4, 2.9],
        "skin",
        {rotation: [72, 0, 10], pivot: [-7.6, 3.4, -8.5]}
    ),
    cube(
        "right_wrist_mass",
        [-8.75, 0.3, -15.1],
        [3.1, 2.6, 3],
        "skin_dark",
        {rotation: [8, 0, -3], pivot: [-7.2, 1.4, -13.6]}
    )
]);

bone("right_hand", "right_forearm", [-7.2, 1.3, -13.8], [
    cube("right_palm", [-9.3, 0.5, -15.9], [4, 2, 4], "skin_dark"),
    cube("right_finger_outer", [-8.9, 0.35, -19.85], [0.75, 0.75, 4.2], "claw"),
    cube("right_finger_mid_outer", [-7.8, 0.35, -20.4], [0.75, 0.75, 4.75], "claw"),
    cube("right_finger_mid_inner", [-6.7, 0.35, -20.25], [0.75, 0.75, 4.6], "claw"),
    cube("right_finger_inner_short", [-5.6, 0.4, -19.05], [0.75, 0.75, 3.4], "claw"),
    cube("right_hand_web_outer", [-8.18, 0.42, -18.1], [0.48, 0.28, 2.45], "webbing"),
    cube("right_hand_web_mid", [-7.08, 0.42, -18.5], [0.48, 0.28, 2.85], "webbing"),
    cube("right_hand_web_inner", [-5.98, 0.42, -17.9], [0.48, 0.28, 2.25], "webbing")
]);

bone("left_rag", "upper_torso", [4.5, 8.5, -4], [
    cube(
        "left_rag_head_drape_upper",
        [2.5, 18.5, -13.5],
        [1.4, 3.5, 2.4],
        "rag",
        {rotation: [-5, 0, -5], pivot: [3.2, 20.5, -12.3]}
    ),
    cube(
        "left_rag_head_drape_mid",
        [2.82, 15.5, -12.9],
        [1.05, 3.5, 2],
        "rag",
        {rotation: [-2, 0, -3], pivot: [3.3, 18.5, -11.9]}
    ),
    cube(
        "left_rag_head_drape_tip",
        [3.05, 13, -12.25],
        [0.65, 3, 1.5],
        "rag_dark",
        {rotation: [4, 0, -7], pivot: [3.35, 15.5, -11.5]}
    ),
    cube(
        "left_rag_shoulder",
        [0.4, 8.6, -7.2],
        [5.8, 0.8, 9.2],
        "rag",
        {rotation: [-8, 0, -5], pivot: [3.3, 9, -2.6]}
    ),
    cube(
        "left_rag_outer_fold",
        [4.4, 1.2, -3],
        [1.1, 9.7, 5.5],
        "rag_dark",
        {rotation: [5, 0, -4], pivot: [4.95, 9, -0.3]}
    )
]);

bone("left_rag_mid", "left_rag", [5.1, 6, 0], [
    cube(
        "left_rag_mid_front",
        [4.2, 1, -2.6],
        [1.1, 8.3, 4.7],
        "rag",
        {rotation: [4, 0, 3], pivot: [4.75, 7.5, -0.3]}
    ),
    cube(
        "left_rag_mid_rear",
        [5.05, 1.8, 1.1],
        [0.7, 6.2, 5],
        "rag_dark",
        {rotation: [-7, 0, -4], pivot: [5.4, 7, 3.4]}
    ),
    cube(
        "left_rag_mid_inner",
        [3.65, 1.6, -4.5],
        [0.85, 6.7, 4],
        "rag",
        {rotation: [6, 0, 5], pivot: [4.05, 7.3, -2.5]}
    )
]);

bone("left_rag_tip", "left_rag_mid", [5, 2.5, 4], [
    cube(
        "left_rag_tip_long",
        [5, 0.5, 3.4],
        [0.65, 5.6, 2.1],
        "rag",
        {rotation: [7, 0, -3], pivot: [5.3, 5.3, 4.3]}
    ),
    cube(
        "left_rag_tip_split",
        [4.05, 0.7, 4.6],
        [0.6, 4.4, 1.7],
        "rag_dark",
        {rotation: [-4, 0, 8], pivot: [4.35, 4.5, 5.2]}
    ),
    cube(
        "left_rag_tip_short",
        [5.75, 1.8, 5.1],
        [0.55, 3.4, 1.5],
        "rag",
        {rotation: [10, 0, -7], pivot: [6, 4.5, 5.5]}
    )
]);

bone("right_rag", "upper_torso", [-4.5, 8.5, -4], [
    cube(
        "right_rag_head_drape_upper",
        [-3.85, 18.8, -13.3],
        [1.3, 3.2, 2.4],
        "rag_dark",
        {rotation: [-3, 0, 6], pivot: [-3.2, 20.3, -12]}
    ),
    cube(
        "right_rag_head_drape_mid",
        [-3.65, 16, -12.65],
        [1, 3.3, 1.9],
        "rag",
        {rotation: [2, 0, 4], pivot: [-3.15, 18.8, -11.8]}
    ),
    cube(
        "right_rag_head_drape_tip",
        [-3.4, 14, -12.1],
        [0.65, 2.6, 1.45],
        "rag_dark",
        {rotation: [-4, 0, 8], pivot: [-3.1, 16.2, -11.4]}
    ),
    cube(
        "right_rag_shoulder",
        [-5.9, 8.4, -6.7],
        [5.4, 0.85, 8.5],
        "rag",
        {rotation: [-8, 0, 7], pivot: [-3.2, 8.8, -2.5]}
    ),
    cube(
        "right_rag_outer_fold",
        [-5.5, 1.8, -2.5],
        [1.15, 8.7, 5],
        "rag_dark",
        {rotation: [5, 0, 5], pivot: [-4.9, 8.7, 0]}
    )
]);

bone("right_rag_mid", "right_rag", [-5.1, 6.1, 0], [
    cube(
        "right_rag_mid_front",
        [-5.35, 1.5, -2.6],
        [0.9, 6.9, 4.3],
        "rag",
        {rotation: [7, 0, -4], pivot: [-4.9, 7, -0.5]}
    ),
    cube(
        "right_rag_mid_rear",
        [-5.85, 1.5, 1],
        [0.7, 6.7, 4.4],
        "rag_dark",
        {rotation: [-6, 0, 5], pivot: [-5.5, 7, 3]}
    )
]);

bone("right_rag_tip", "right_rag_mid", [-5.1, 2.7, 4], [
    cube(
        "right_rag_tip_long",
        [-5.7, 1, 3.2],
        [0.65, 4.8, 1.9],
        "rag",
        {rotation: [5, 0, 5], pivot: [-5.38, 5.2, 4]}
    ),
    cube(
        "right_rag_tip_short",
        [-4.75, 1.8, 4.2],
        [0.6, 3.4, 1.6],
        "rag_dark",
        {rotation: [-5, 0, -8], pivot: [-4.45, 4.6, 4.8]}
    )
]);

bone("back_rag", "upper_torso", [0, 8.5, -1.5], [
    cube(
        "back_rag_collar",
        [-4.5, 8.8, -5.2],
        [9, 0.8, 9.8],
        "rag",
        {rotation: [-8, 0, 0], pivot: [0, 9.2, -0.5]}
    ),
    cube(
        "back_rag_upper",
        [-4, 7.2, -0.6],
        [8, 0.8, 10],
        "rag_dark",
        {rotation: [7, 0, 0], pivot: [0, 7.6, 3.9]}
    ),
    cube(
        "back_rag_left_overlap",
        [0.2, 5.2, 1.5],
        [3.6, 0.7, 9.2],
        "rag",
        {rotation: [11, 0, -3], pivot: [2, 5.6, 5.8]}
    ),
    cube(
        "back_rag_right_overlap",
        [-3.8, 5.5, 1],
        [3.5, 0.7, 8.4],
        "rag",
        {rotation: [9, 0, 4], pivot: [-2, 5.9, 5]}
    )
]);

bone("back_rag_lower", "back_rag", [0, 6, 6], [
    cube(
        "back_rag_lower_centre",
        [-1.2, 0.8, 5],
        [2.4, 8, 0.75],
        "rag",
        {rotation: [-4, 0, 0], pivot: [0, 7.2, 5.4]}
    ),
    cube(
        "back_rag_lower_left",
        [1.35, 1.2, 5.7],
        [1.8, 6.8, 0.7],
        "rag_dark",
        {rotation: [5, 0, -5], pivot: [2.2, 7, 6]}
    ),
    cube(
        "back_rag_lower_right",
        [-3.3, 1.8, 5.4],
        [1.9, 5.8, 0.7],
        "rag",
        {rotation: [-6, 0, 7], pivot: [-2.35, 7, 5.8]}
    ),
    cube(
        "back_rag_lower_short",
        [3.15, 3.5, 6.1],
        [1.2, 4.2, 0.6],
        "rag",
        {rotation: [8, 0, -8], pivot: [3.7, 7.2, 6.4]}
    )
]);

const allCubes = bones.flatMap((entry) =>
    entry.cubes.map((modelCube) => ({bone: entry.name, cube: modelCube}))
);

function packUvs() {
    const items = allCubes.map(({bone: boneName, cube: modelCube}) => {
        const [width, height, depth] = modelCube.size;
        const innerWidth = Math.ceil(2 * (width + depth));
        const innerHeight = Math.ceil(height + depth);
        return {
            boneName,
            modelCube,
            innerWidth,
            innerHeight,
            width: innerWidth + 2,
            height: innerHeight + 2
        };
    }).sort((left, right) =>
        Math.max(right.width, right.height)
        - Math.max(left.width, left.height)
        || right.width * right.height - left.width * left.height
    );

    let freeRectangles = [
        {x: 0, y: 0, width: textureWidth, height: textureHeight}
    ];

    function intersects(left, right) {
        return (
            left.x < right.x + right.width
            && left.x + left.width > right.x
            && left.y < right.y + right.height
            && left.y + left.height > right.y
        );
    }

    function contains(outer, inner) {
        return (
            inner.x >= outer.x
            && inner.y >= outer.y
            && inner.x + inner.width <= outer.x + outer.width
            && inner.y + inner.height <= outer.y + outer.height
        );
    }

    for (const item of items) {
        let selectedRectangle;
        let selectedScore = Infinity;
        for (const rectangle of freeRectangles) {
            if (
                item.width <= rectangle.width
                && item.height <= rectangle.height
            ) {
                const shortSide = Math.min(
                    rectangle.width - item.width,
                    rectangle.height - item.height
                );
                const longSide = Math.max(
                    rectangle.width - item.width,
                    rectangle.height - item.height
                );
                const score = shortSide * 1000 + longSide;
                if (score < selectedScore) {
                    selectedRectangle = rectangle;
                    selectedScore = score;
                }
            }
        }

        if (!selectedRectangle) {
            throw new Error(
                `UV layout exceeds ${textureWidth}x${textureHeight} at ${item.modelCube.name}`
            );
        }

        item.x = selectedRectangle.x;
        item.y = selectedRectangle.y;
        item.modelCube.uv = [item.x + 1, item.y + 1];
        item.modelCube.uvRect = {
            x: item.x,
            y: item.y,
            width: item.width,
            height: item.height,
            innerWidth: item.innerWidth,
            innerHeight: item.innerHeight
        };

        const placed = {
            x: item.x,
            y: item.y,
            width: item.width,
            height: item.height
        };
        const splitRectangles = [];
        for (const rectangle of freeRectangles) {
            if (!intersects(rectangle, placed)) {
                splitRectangles.push(rectangle);
                continue;
            }

            if (placed.x > rectangle.x) {
                splitRectangles.push({
                    x: rectangle.x,
                    y: rectangle.y,
                    width: placed.x - rectangle.x,
                    height: rectangle.height
                });
            }
            if (
                placed.x + placed.width
                < rectangle.x + rectangle.width
            ) {
                splitRectangles.push({
                    x: placed.x + placed.width,
                    y: rectangle.y,
                    width:
                        rectangle.x + rectangle.width
                        - placed.x - placed.width,
                    height: rectangle.height
                });
            }
            if (placed.y > rectangle.y) {
                splitRectangles.push({
                    x: rectangle.x,
                    y: rectangle.y,
                    width: rectangle.width,
                    height: placed.y - rectangle.y
                });
            }
            if (
                placed.y + placed.height
                < rectangle.y + rectangle.height
            ) {
                splitRectangles.push({
                    x: rectangle.x,
                    y: placed.y + placed.height,
                    width: rectangle.width,
                    height:
                        rectangle.y + rectangle.height
                        - placed.y - placed.height
                });
            }
        }

        freeRectangles = splitRectangles.filter(
            (candidate, candidateIndex, rectangles) =>
                candidate.width > 0
                && candidate.height > 0
                && !rectangles.some(
                    (other, otherIndex) =>
                        candidateIndex !== otherIndex
                        && contains(other, candidate)
                )
        );
    }

    return items;
}

const uvItems = packUvs();

function cubeForJson(modelCube) {
    const result = {
        origin: modelCube.origin,
        size: modelCube.size,
        uv: modelCube.uv
    };

    if (modelCube.pivot) {
        result.pivot = modelCube.pivot;
    }
    if (modelCube.rotation) {
        result.rotation = modelCube.rotation;
    }
    if (modelCube.inflate !== undefined) {
        result.inflate = modelCube.inflate;
    }
    if (modelCube.mirror !== undefined) {
        result.mirror = modelCube.mirror;
    }

    return result;
}

const geometry = {
    format_version: "1.12.0",
    "minecraft:geometry": [
        {
            description: {
                identifier: "geometry.goetyarkham.young_deep_one",
                texture_width: textureWidth,
                texture_height: textureHeight,
                visible_bounds_width: 3,
                visible_bounds_height: 2.5,
                visible_bounds_offset: [0, 0.75, -0.25]
            },
            bones: bones.map((entry) => {
                const result = {
                    name: entry.name,
                    pivot: entry.pivot
                };
                if (entry.parent) {
                    result.parent = entry.parent;
                }
                if (entry.cubes.length > 0) {
                    result.cubes = entry.cubes.map(cubeForJson);
                }
                return result;
            })
        }
    ]
};

function deterministicUuid(key) {
    const hash = crypto
        .createHash("sha256")
        .update(`goetyarkham:young_deep_one:${key}`)
        .digest("hex")
        .slice(0, 32)
        .split("");
    hash[12] = "5";
    hash[16] = ((Number.parseInt(hash[16], 16) & 0x3) | 0x8).toString(16);
    const value = hash.join("");
    return [
        value.slice(0, 8),
        value.slice(8, 12),
        value.slice(12, 16),
        value.slice(16, 20),
        value.slice(20)
    ].join("-");
}

function blockbenchVector(vector) {
    return [-vector[0], vector[1], vector[2]];
}

function blockbenchRotation(rotation) {
    return [-rotation[0], -rotation[1], rotation[2]];
}

function blockbenchCubeBounds(modelCube) {
    const from = [
        -(modelCube.origin[0] + modelCube.size[0]),
        modelCube.origin[1],
        modelCube.origin[2]
    ];
    return {
        from,
        to: from.map((value, axis) => value + modelCube.size[axis])
    };
}

function makeBoxUvFaces(modelCube) {
    const [width, height, depth] = modelCube.size.map(Math.ceil);
    const faces = [
        {name: "east", from: [0, depth], size: [depth, height]},
        {
            name: "west",
            from: [depth + width, depth],
            size: [depth, height]
        },
        {
            name: "up",
            from: [depth + width, depth],
            size: [-width, -depth]
        },
        {
            name: "down",
            from: [depth + width * 2, 0],
            size: [-width, depth]
        },
        {
            name: "south",
            from: [depth * 2 + width, depth],
            size: [width, height]
        },
        {
            name: "north",
            from: [depth, depth],
            size: [width, height]
        }
    ];

    if (modelCube.mirror) {
        for (const face of faces) {
            face.from[0] += face.size[0];
            face.size[0] *= -1;
        }
        [faces[0].from, faces[1].from] = [faces[1].from, faces[0].from];
        [faces[0].size, faces[1].size] = [faces[1].size, faces[0].size];
    }

    return Object.fromEntries(faces.map((face) => {
        const u = face.from[0] + modelCube.uv[0];
        const v = face.from[1] + modelCube.uv[1];
        return [
            face.name,
            {
                uv: [u, v, u + face.size[0], v + face.size[1]],
                texture: 0
            }
        ];
    }));
}

function makeBbmodel(textureBuffer) {
    const boneUuids = new Map(
        bones.map((entry) => [
            entry.name,
            deterministicUuid(`bone:${entry.name}`)
        ])
    );
    const cubeUuids = new Map();
    for (const entry of bones) {
        entry.cubes.forEach((modelCube, cubeIndex) => {
            cubeUuids.set(
                modelCube,
                deterministicUuid(
                    `cube:${entry.name}:${cubeIndex}:${modelCube.name}`
                )
            );
        });
    }

    const childBones = new Map(bones.map((entry) => [entry.name, []]));
    for (const entry of bones) {
        if (entry.parent) {
            childBones.get(entry.parent).push(entry);
        }
    }

    function outlinerNode(entry) {
        return {
            uuid: boneUuids.get(entry.name),
            isOpen: true,
            children: [
                ...entry.cubes.map((modelCube) => cubeUuids.get(modelCube)),
                ...childBones.get(entry.name).map(outlinerNode)
            ]
        };
    }

    const elements = [];
    bones.forEach((entry, boneIndex) => {
        entry.cubes.forEach((modelCube) => {
            const bounds = blockbenchCubeBounds(modelCube);
            const element = {
                name: modelCube.name,
                box_uv: true,
                from: bounds.from,
                to: bounds.to,
                autouv: 0,
                color: boneIndex % 8,
                origin: modelCube.pivot
                    ? blockbenchVector(modelCube.pivot)
                    : [0, 0, 0],
                uv_offset: modelCube.uv,
                faces: makeBoxUvFaces(modelCube),
                type: "cube",
                uuid: cubeUuids.get(modelCube)
            };
            if (modelCube.rotation) {
                element.rotation = blockbenchRotation(modelCube.rotation);
            }
            if (modelCube.inflate !== undefined) {
                element.inflate = modelCube.inflate;
            }
            if (modelCube.mirror) {
                element.mirror_uv = true;
            }
            elements.push(element);
        });
    });

    const groups = bones.map((entry, boneIndex) => ({
        name: entry.name,
        origin: blockbenchVector(entry.pivot),
        color: boneIndex % 8,
        uuid: boneUuids.get(entry.name)
    }));

    const textureUuid = deterministicUuid("texture:young_deep_one.png");
    const textureRelativePath = path
        .relative(path.dirname(bbmodelPath), texturePath)
        .split(path.sep)
        .join("/");

    return {
        meta: {
            format_version: "5.0",
            model_format: "geckolib_model",
            box_uv: true
        },
        name: "young_deep_one",
        model_identifier: "young_deep_one",
        geckolib_modid: "goetyarkham",
        geckolib_model_type: "Entity",
        geckolib_filepath_cache: {},
        resolution: {
            width: textureWidth,
            height: textureHeight
        },
        elements,
        groups,
        outliner: bones
            .filter((entry) => !entry.parent)
            .map(outlinerNode),
        textures: [
            {
                relative_path: textureRelativePath,
                name: "young_deep_one.png",
                folder: "entity",
                namespace: "goetyarkham",
                id: "0",
                width: textureWidth,
                height: textureHeight,
                uv_width: textureWidth,
                uv_height: textureHeight,
                particle: false,
                use_as_default: true,
                render_mode: "default",
                render_sides: "auto",
                frame_time: 1,
                frame_order_type: "loop",
                frame_interpolate: false,
                visible: true,
                internal: true,
                saved: true,
                uuid: textureUuid,
                source:
                    `data:image/png;base64,${textureBuffer.toString("base64")}`
            }
        ],
        animations: []
    };
}

function crc32(buffer) {
    let crc = 0xffffffff;
    for (const byte of buffer) {
        crc ^= byte;
        for (let bit = 0; bit < 8; bit += 1) {
            crc = (crc >>> 1) ^ (0xedb88320 & -(crc & 1));
        }
    }
    return (crc ^ 0xffffffff) >>> 0;
}

function pngChunk(type, data) {
    const typeBuffer = Buffer.from(type, "ascii");
    const length = Buffer.alloc(4);
    length.writeUInt32BE(data.length);
    const checksum = Buffer.alloc(4);
    checksum.writeUInt32BE(crc32(Buffer.concat([typeBuffer, data])));
    return Buffer.concat([length, typeBuffer, data, checksum]);
}

function clampChannel(value) {
    return Math.max(0, Math.min(255, value));
}

function encodeRgbaPng(width, height, pixels) {
    const raw = Buffer.alloc((width * 4 + 1) * height);
    for (let y = 0; y < height; y += 1) {
        const rowOffset = y * (width * 4 + 1);
        raw[rowOffset] = 0;
        pixels.copy(
            raw,
            rowOffset + 1,
            y * width * 4,
            (y + 1) * width * 4
        );
    }

    const ihdr = Buffer.alloc(13);
    ihdr.writeUInt32BE(width, 0);
    ihdr.writeUInt32BE(height, 4);
    ihdr[8] = 8;
    ihdr[9] = 6;
    ihdr[10] = 0;
    ihdr[11] = 0;
    ihdr[12] = 0;

    return Buffer.concat([
        Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]),
        pngChunk("IHDR", ihdr),
        pngChunk("IDAT", zlib.deflateSync(raw, {level: 9})),
        pngChunk("IEND", Buffer.alloc(0))
    ]);
}

function makeTexture() {
    const pixels = Buffer.alloc(textureWidth * textureHeight * 4);

    function setPixel(x, y, colour) {
        if (x < 0 || y < 0 || x >= textureWidth || y >= textureHeight) {
            return;
        }
        const index = (y * textureWidth + x) * 4;
        pixels[index] = colour[0];
        pixels[index + 1] = colour[1];
        pixels[index + 2] = colour[2];
        pixels[index + 3] = colour[3];
    }

    for (let y = 0; y < textureHeight; y += 1) {
        for (let x = 0; x < textureWidth; x += 1) {
            const checker = (Math.floor(x / 4) + Math.floor(y / 4)) % 2;
            setPixel(
                x,
                y,
                checker === 0
                    ? [255, 0, 220, 255]
                    : [30, 10, 30, 255]
            );
        }
    }

    for (const item of uvItems) {
        const {modelCube, x, y, innerWidth, innerHeight} = item;
        const base = materialColours[modelCube.material];

        for (let py = y; py < y + item.height; py += 1) {
            for (let px = x; px < x + item.width; px += 1) {
                setPixel(px, py, [8, 8, 8, 255]);
            }
        }

        for (let py = 0; py < innerHeight; py += 1) {
            for (let px = 0; px < innerWidth; px += 1) {
                const variation = ((px + py) % 5 === 0) ? 12 : 0;
                setPixel(
                    x + 1 + px,
                    y + 1 + py,
                    [
                        clampChannel(base[0] + variation),
                        clampChannel(base[1] + variation),
                        clampChannel(base[2] + variation),
                        base[3]
                    ]
                );
            }
        }

        const [cubeWidth, cubeHeight, cubeDepth] =
            modelCube.size.map((value) => Math.ceil(value));
        const u = x + 1;
        const v = y + 1;
        const verticalLines = [
            u + cubeDepth,
            u + cubeDepth + cubeWidth,
            u + 2 * cubeDepth + cubeWidth
        ];
        for (const lineX of verticalLines) {
            for (
                let lineY = v + cubeDepth;
                lineY < v + innerHeight;
                lineY += 1
            ) {
                setPixel(lineX, lineY, [12, 12, 12, 255]);
            }
        }
        for (let lineX = u; lineX < u + innerWidth; lineX += 1) {
            setPixel(lineX, v + cubeDepth, [12, 12, 12, 255]);
        }

        const hash = [...modelCube.name].reduce(
            (value, character) =>
                ((value * 31) + character.charCodeAt(0)) >>> 0,
            7
        );
        for (let marker = 0; marker < 3; marker += 1) {
            const markerX = u + 1 + ((hash >>> (marker * 5)) % Math.max(1, innerWidth - 2));
            const markerY = v + 1 + ((hash >>> (marker * 7)) % Math.max(1, innerHeight - 2));
            setPixel(markerX, markerY, [235, 235, 235, 255]);
        }
    }

    return encodeRgbaPng(textureWidth, textureHeight, pixels);
}

function escapeXml(value) {
    return value
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll("\"", "&quot;");
}

function makeUvGuide() {
    const rectangles = uvItems.map((item) => {
        const colour = materialColours[item.modelCube.material];
        const fill = `rgb(${colour[0]},${colour[1]},${colour[2]})`;
        return [
            `<rect x="${item.x}" y="${item.y}" width="${item.width}" height="${item.height}"`,
            ` fill="${fill}" stroke="#111" stroke-width="0.25"/>`,
            `<text x="${item.x + 0.7}" y="${item.y + 1.7}" font-size="1.35"`,
            ` fill="${item.modelCube.material === "rag" ? "#111" : "#fff"}">`,
            `${escapeXml(item.modelCube.name)}</text>`
        ].join("");
    }).join("\n  ");

    return [
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
        `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${textureWidth} ${textureHeight}"`,
        " width=\"1024\" height=\"1024\">",
        "  <rect width=\"128\" height=\"128\" fill=\"#2a1028\"/>",
        `  ${rectangles}`,
        "</svg>",
        ""
    ].join("\n");
}

function rotatePoint(point, rotation, pivot) {
    const radians = rotation.map((degrees) => degrees * Math.PI / 180);
    let [x, y, z] = point.map((value, index) => value - pivot[index]);

    const [rx, ry, rz] = radians;
    [y, z] = [
        y * Math.cos(rx) - z * Math.sin(rx),
        y * Math.sin(rx) + z * Math.cos(rx)
    ];
    [x, z] = [
        x * Math.cos(ry) + z * Math.sin(ry),
        -x * Math.sin(ry) + z * Math.cos(ry)
    ];
    [x, y] = [
        x * Math.cos(rz) - y * Math.sin(rz),
        x * Math.sin(rz) + y * Math.cos(rz)
    ];

    return [x + pivot[0], y + pivot[1], z + pivot[2]];
}

function transformedCubeCorners(modelCube) {
    const [ox, oy, oz] = modelCube.origin;
    const [sx, sy, sz] = modelCube.size;
    const points = [
        [ox, oy, oz],
        [ox + sx, oy, oz],
        [ox + sx, oy + sy, oz],
        [ox, oy + sy, oz],
        [ox, oy, oz + sz],
        [ox + sx, oy, oz + sz],
        [ox + sx, oy + sy, oz + sz],
        [ox, oy + sy, oz + sz]
    ];

    if (!modelCube.rotation) {
        return points;
    }
    return points.map((point) =>
        rotatePoint(point, modelCube.rotation, modelCube.pivot)
    );
}

function makePerspectiveView(camera, target, light) {
    function normalise(vector) {
        const length = Math.hypot(...vector);
        return vector.map((value) => value / length);
    }

    function cross(left, right) {
        return [
            left[1] * right[2] - left[2] * right[1],
            left[2] * right[0] - left[0] * right[2],
            left[0] * right[1] - left[1] * right[0]
        ];
    }

    function dot(left, right) {
        return left.reduce(
            (sum, value, axis) => sum + value * right[axis],
            0
        );
    }

    const forward = normalise(
        target.map((value, axis) => value - camera[axis])
    );
    const right = normalise(cross([0, 1, 0], forward));
    const up = normalise(cross(forward, right));

    return {
        project(point) {
            const relative = point.map(
                (value, axis) => value - camera[axis]
            );
            const depth = dot(relative, forward);
            return [
                dot(relative, right) / depth,
                -dot(relative, up) / depth,
                depth
            ];
        },
        light,
        depthOrder: "descending",
        floorLines: [
            [[-18, 0, -20], [18, 0, -20]],
            [[-18, 0, -4], [18, 0, -4]],
            [[-18, 0, 10], [18, 0, 10]]
        ]
    };
}

const previewViews = {
    front: {
        project: ([x, y, z]) => [x, -y, -z],
        light: [0.88, 1, 0.92, 1.08, 0.8, 0.96],
        floorLines: [[[-14, 0, 0], [14, 0, 0]]]
    },
    right: {
        project: ([x, y, z]) => [z, -y, x],
        light: [0.82, 1.04, 0.9, 1.08, 0.98, 0.84],
        floorLines: [[[0, 0, -22], [0, 0, 12]]]
    },
    back: {
        project: ([x, y, z]) => [-x, -y, z],
        light: [1.02, 0.84, 0.9, 1.06, 0.96, 0.8],
        floorLines: [[[-14, 0, 0], [14, 0, 0]]]
    },
    leftFront: makePerspectiveView(
        [34, 18, -42],
        [0, 7, -4],
        [0.88, 1.04, 0.92, 1.08, 0.84, 0.96]
    ),
    lowAngle: makePerspectiveView(
        [27, 4.2, -37],
        [0, 7.5, -4],
        [0.82, 1.08, 0.9, 1.02, 0.88, 0.96]
    )
};

function makePreview(views = [
    previewViews.front,
    previewViews.right,
    previewViews.back
], options = {}) {
    const width = options.width ?? views.length * 512;
    const height = options.height ?? 512;
    const panelWidth = width / views.length;
    const pixels = Buffer.alloc(width * height * 4);

    function setPixel(x, y, colour) {
        const px = Math.round(x);
        const py = Math.round(y);
        if (px < 0 || py < 0 || px >= width || py >= height) {
            return;
        }
        const index = (py * width + px) * 4;
        pixels[index] = colour[0];
        pixels[index + 1] = colour[1];
        pixels[index + 2] = colour[2];
        pixels[index + 3] = colour[3] ?? 255;
    }

    function fillPolygon(points, colour) {
        const minimumY = Math.max(
            0,
            Math.floor(Math.min(...points.map((point) => point[1])))
        );
        const maximumY = Math.min(
            height - 1,
            Math.ceil(Math.max(...points.map((point) => point[1])))
        );

        for (let y = minimumY; y <= maximumY; y += 1) {
            const intersections = [];
            for (let index = 0; index < points.length; index += 1) {
                const current = points[index];
                const next = points[(index + 1) % points.length];
                if (
                    (current[1] <= y && next[1] > y)
                    || (next[1] <= y && current[1] > y)
                ) {
                    const ratio = (y - current[1]) / (next[1] - current[1]);
                    intersections.push(
                        current[0] + ratio * (next[0] - current[0])
                    );
                }
            }
            intersections.sort((left, right) => left - right);
            for (let index = 0; index + 1 < intersections.length; index += 2) {
                const start = Math.ceil(intersections[index]);
                const end = Math.floor(intersections[index + 1]);
                for (let x = start; x <= end; x += 1) {
                    setPixel(x, y, colour);
                }
            }
        }
    }

    function drawLine(start, end, colour) {
        const steps = Math.max(
            1,
            Math.ceil(
                Math.max(
                    Math.abs(end[0] - start[0]),
                    Math.abs(end[1] - start[1])
                )
            )
        );
        for (let step = 0; step <= steps; step += 1) {
            const ratio = step / steps;
            setPixel(
                start[0] + (end[0] - start[0]) * ratio,
                start[1] + (end[1] - start[1]) * ratio,
                colour
            );
        }
    }

    for (let y = 0; y < height; y += 1) {
        for (let x = 0; x < width; x += 1) {
            const panel = Math.floor(x / panelWidth);
            const shade = panel % 2 === 0 ? 238 : 229;
            setPixel(x, y, [shade, shade, shade - 3, 255]);
        }
    }

    const faces = [
        [0, 1, 2, 3],
        [4, 7, 6, 5],
        [0, 4, 5, 1],
        [3, 2, 6, 7],
        [1, 5, 6, 2],
        [0, 3, 7, 4]
    ];

    views.forEach((view, viewIndex) => {
        const projectedCorners = allCubes.flatMap(({cube: modelCube}) =>
            transformedCubeCorners(modelCube).map(view.project)
        );
        const minimumX = Math.min(...projectedCorners.map((point) => point[0]));
        const maximumX = Math.max(...projectedCorners.map((point) => point[0]));
        const minimumY = Math.min(...projectedCorners.map((point) => point[1]));
        const maximumY = Math.max(...projectedCorners.map((point) => point[1]));
        const scale = Math.min(
            (panelWidth - 50) / (maximumX - minimumX),
            (height - 50) / (maximumY - minimumY)
        );
        const offsetX =
            viewIndex * panelWidth
            + (panelWidth - (maximumX - minimumX) * scale) / 2
            - minimumX * scale;
        const offsetY =
            (height - (maximumY - minimumY) * scale) / 2
            - minimumY * scale;

        const projectedFaces = [];
        for (const {cube: modelCube} of allCubes) {
            const corners = transformedCubeCorners(modelCube).map(view.project);
            faces.forEach((face, faceIndex) => {
                const points = face.map((cornerIndex) => corners[cornerIndex]);
                const depth =
                    points.reduce((sum, point) => sum + point[2], 0)
                    / points.length;
                projectedFaces.push({
                    points: points.map((point) => [
                        offsetX + point[0] * scale,
                        offsetY + point[1] * scale
                    ]),
                    depth,
                    material: modelCube.material,
                    faceIndex
                });
            });
        }
        projectedFaces.sort((left, right) =>
            view.depthOrder === "descending"
                ? right.depth - left.depth
                : left.depth - right.depth
        );

        for (const face of projectedFaces) {
            const base = materialColours[face.material];
            const brightness = view.light[face.faceIndex];
            const colour = [
                clampChannel(Math.round(base[0] * brightness)),
                clampChannel(Math.round(base[1] * brightness)),
                clampChannel(Math.round(base[2] * brightness)),
                255
            ];
            fillPolygon(face.points, colour);
            for (let index = 0; index < face.points.length; index += 1) {
                drawLine(
                    face.points[index],
                    face.points[(index + 1) % face.points.length],
                    [22, 25, 22, 255]
                );
            }
        }

        for (const floorLine of view.floorLines ?? []) {
            const projectedLine = floorLine.map(view.project);
            drawLine(
                [
                    offsetX + projectedLine[0][0] * scale,
                    offsetY + projectedLine[0][1] * scale
                ],
                [
                    offsetX + projectedLine[1][0] * scale,
                    offsetY + projectedLine[1][1] * scale
                ],
                [115, 115, 110, 255]
            );
        }
    });

    return encodeRgbaPng(width, height, pixels);
}

function calculateBounds() {
    const minimum = [Infinity, Infinity, Infinity];
    const maximum = [-Infinity, -Infinity, -Infinity];

    for (const {cube: modelCube} of allCubes) {
        const corners = transformedCubeCorners(modelCube);
        for (const corner of corners) {
            for (let axis = 0; axis < 3; axis += 1) {
                minimum[axis] = Math.min(minimum[axis], corner[axis]);
                maximum[axis] = Math.max(maximum[axis], corner[axis]);
            }
        }
    }

    return {
        minimum,
        maximum,
        size: maximum.map((value, index) => value - minimum[index])
    };
}

function cubesBelowGround(clearance = 0) {
    return allCubes
        .map(({bone: boneName, cube: modelCube}) => ({
            boneName,
            cubeName: modelCube.name,
            minimumY: Math.min(
                ...transformedCubeCorners(modelCube).map((point) => point[1])
            )
        }))
        .filter((entry) => entry.minimumY < clearance)
        .sort((left, right) => left.minimumY - right.minimumY);
}

function cubesAboveHeight(height) {
    return allCubes
        .map(({bone: boneName, cube: modelCube}) => ({
            boneName,
            cubeName: modelCube.name,
            maximumY: Math.max(
                ...transformedCubeCorners(modelCube).map((point) => point[1])
            )
        }))
        .filter((entry) => entry.maximumY > height)
        .sort((left, right) => right.maximumY - left.maximumY);
}

function cubeVerticalBounds(cubeName) {
    const entry = allCubes.find(
        ({cube: modelCube}) => modelCube.name === cubeName
    );
    if (!entry) {
        throw new Error(`Missing cube for vertical bounds: ${cubeName}`);
    }
    const yValues = transformedCubeCorners(entry.cube).map(
        (point) => point[1]
    );
    return {
        minimum: Math.min(...yValues),
        maximum: Math.max(...yValues)
    };
}

function validateStaticPose(bounds) {
    const tolerance = 1e-4;
    if (bones.length !== 28 || allCubes.length !== 93) {
        throw new Error(
            `Static pose changed topology: ${bones.length} bones, `
            + `${allCubes.length} cubes`
        );
    }
    if (Math.abs(bounds.maximum[1] - 24) > tolerance) {
        throw new Error(
            `Static pose height must be 24, received ${bounds.maximum[1]}`
        );
    }

    const underground = cubesBelowGround(-tolerance);
    if (underground.length > 0) {
        throw new Error(
            "Static pose intersects the ground: "
            + underground
                .map((entry) =>
                    `${entry.cubeName}=${entry.minimumY.toFixed(3)}`
                )
                .join(", ")
        );
    }

    const supportCubeNames = [
        "left_palm",
        "right_palm",
        "left_sole",
        "right_sole",
        "left_toe_outer",
        "left_toe_mid_outer",
        "left_toe_mid_inner",
        "left_toe_inner",
        "right_toe_outer",
        "right_toe_mid_outer",
        "right_toe_mid_inner",
        "right_toe_inner"
    ];
    for (const cubeName of supportCubeNames) {
        const verticalBounds = cubeVerticalBounds(cubeName);
        if (verticalBounds.minimum < -tolerance) {
            throw new Error(
                `${cubeName} sinks below ground: `
                + verticalBounds.minimum.toFixed(3)
            );
        }
    }

    const torsoClearance = Math.min(
        cubeVerticalBounds("chest").minimum,
        cubeVerticalBounds("abdomen").minimum,
        cubeVerticalBounds("belly").minimum
    );
    if (torsoClearance < 1 || torsoClearance > 2) {
        throw new Error(
            `Torso clearance must be 1-2, received `
            + torsoClearance.toFixed(3)
        );
    }

    const expectedLimbPivots = {
        left_upper_arm: [4.8, 6.8, -5],
        left_forearm: [7.6, 3.4, -8.5],
        left_hand: [7.2, 1.3, -13.8],
        right_upper_arm: [-4.8, 6.8, -5],
        right_forearm: [-7.6, 3.4, -8.5],
        right_hand: [-7.2, 1.3, -13.8],
        left_upper_leg: [3.5, 5.3, 2.2],
        left_lower_leg: [5.4, 2.5, 6.1],
        left_foot: [5.2, 1.1, 1.5],
        right_upper_leg: [-3.5, 5.3, 2.2],
        right_lower_leg: [-5.4, 2.5, 6.1],
        right_foot: [-5.2, 1.1, 1.5]
    };
    for (const [boneName, expectedPivot] of Object.entries(
        expectedLimbPivots
    )) {
        const entry = bones.find((candidate) => candidate.name === boneName);
        if (!entry) {
            throw new Error(`Missing posed limb bone: ${boneName}`);
        }
        if (JSON.stringify(entry.pivot) !== JSON.stringify(expectedPivot)) {
            throw new Error(
                `${boneName} pivot mismatch: `
                + `${JSON.stringify(entry.pivot)}`
            );
        }
    }

    const leftShoulder = expectedLimbPivots.left_upper_arm[0];
    const rightShoulder = expectedLimbPivots.right_upper_arm[0];
    const leftHand = expectedLimbPivots.left_hand[0];
    const rightHand = expectedLimbPivots.right_hand[0];
    const handSpreadRatio =
        Math.abs(leftHand - rightHand)
        / Math.abs(leftShoulder - rightShoulder);
    if (handSpreadRatio < 1.3 || handSpreadRatio > 1.6) {
        throw new Error(
            `Hand support spread ratio out of range: `
            + handSpreadRatio.toFixed(3)
        );
    }

    return {
        torsoClearance,
        handSpreadRatio,
        leftPalmClearance: cubeVerticalBounds("left_palm").minimum,
        rightPalmClearance: cubeVerticalBounds("right_palm").minimum,
        leftSoleClearance: cubeVerticalBounds("left_sole").minimum,
        rightSoleClearance: cubeVerticalBounds("right_sole").minimum
    };
}

function validateModel() {
    const boneNames = new Set();
    for (const entry of bones) {
        if (boneNames.has(entry.name)) {
            throw new Error(`Duplicate bone name: ${entry.name}`);
        }
        if (entry.parent && !boneNames.has(entry.parent)) {
            throw new Error(
                `Bone ${entry.name} references missing or later parent ${entry.parent}`
            );
        }
        boneNames.add(entry.name);
    }

    for (let left = 0; left < uvItems.length; left += 1) {
        const a = uvItems[left];
        if (
            a.x < 0
            || a.y < 0
            || a.x + a.width > textureWidth
            || a.y + a.height > textureHeight
        ) {
            throw new Error(`UV rectangle out of bounds: ${a.modelCube.name}`);
        }
        for (let right = left + 1; right < uvItems.length; right += 1) {
            const b = uvItems[right];
            const overlap =
                a.x < b.x + b.width
                && a.x + a.width > b.x
                && a.y < b.y + b.height
                && a.y + a.height > b.y;
            if (overlap) {
                throw new Error(
                    `UV overlap: ${a.modelCube.name} and ${b.modelCube.name}`
                );
            }
        }
    }
}

function validateBbmodel(bbmodel, textureBuffer) {
    function normaliseNumbers(value) {
        if (typeof value === "number") {
            return Math.round(value * 1e9) / 1e9;
        }
        if (Array.isArray(value)) {
            return value.map(normaliseNumbers);
        }
        if (value && typeof value === "object") {
            return Object.fromEntries(
                Object.entries(value).map(([key, child]) => [
                    key,
                    normaliseNumbers(child)
                ])
            );
        }
        return value;
    }

    function assertEqual(actual, expected, label) {
        if (
            JSON.stringify(normaliseNumbers(actual))
            !== JSON.stringify(normaliseNumbers(expected))
        ) {
            throw new Error(
                `${label} mismatch: expected ${JSON.stringify(expected)}, `
                + `received ${JSON.stringify(actual)}`
            );
        }
    }

    assertEqual(bbmodel.meta, {
        format_version: "5.0",
        model_format: "geckolib_model",
        box_uv: true
    }, "Blockbench metadata");
    assertEqual(bbmodel.geckolib_model_type, "Entity", "GeckoLib model type");
    assertEqual(bbmodel.resolution, {
        width: textureWidth,
        height: textureHeight
    }, "Blockbench texture resolution");
    assertEqual(bbmodel.animations, [], "Blockbench animations");

    if (bbmodel.groups.length !== bones.length) {
        throw new Error(
            `Blockbench group count mismatch: ${bbmodel.groups.length}`
        );
    }
    if (bbmodel.elements.length !== allCubes.length) {
        throw new Error(
            `Blockbench cube count mismatch: ${bbmodel.elements.length}`
        );
    }

    const groupByUuid = new Map(
        bbmodel.groups.map((group) => [group.uuid, group])
    );
    const elementByUuid = new Map(
        bbmodel.elements.map((element) => [element.uuid, element])
    );
    const allUuids = [
        ...groupByUuid.keys(),
        ...elementByUuid.keys(),
        ...bbmodel.textures.map((texture) => texture.uuid)
    ];
    if (new Set(allUuids).size !== allUuids.length) {
        throw new Error("Blockbench UUIDs are not unique");
    }

    const groupParentNames = new Map();
    const cubeParentNames = new Map();
    function visitOutliner(nodes, parentName) {
        for (const node of nodes) {
            if (typeof node === "string") {
                if (!elementByUuid.has(node)) {
                    throw new Error(
                        `Unknown Blockbench element UUID in outliner: ${node}`
                    );
                }
                cubeParentNames.set(node, parentName);
                continue;
            }

            const group = groupByUuid.get(node.uuid);
            if (!group) {
                throw new Error(
                    `Unknown Blockbench group UUID in outliner: ${node.uuid}`
                );
            }
            groupParentNames.set(group.name, parentName);
            visitOutliner(node.children ?? [], group.name);
        }
    }
    visitOutliner(bbmodel.outliner, undefined);

    const seenElementUuids = new Set();
    for (const entry of bones) {
        const group = bbmodel.groups.find(
            (candidate) => candidate.name === entry.name
        );
        if (!group) {
            throw new Error(`Missing Blockbench group: ${entry.name}`);
        }
        assertEqual(
            group.origin,
            blockbenchVector(entry.pivot),
            `${entry.name} Blockbench pivot`
        );
        assertEqual(
            groupParentNames.get(entry.name),
            entry.parent,
            `${entry.name} Blockbench parent`
        );

        entry.cubes.forEach((modelCube, cubeIndex) => {
            const uuid = deterministicUuid(
                `cube:${entry.name}:${cubeIndex}:${modelCube.name}`
            );
            const element = elementByUuid.get(uuid);
            if (!element) {
                throw new Error(
                    `Missing Blockbench cube: ${entry.name}/${modelCube.name}`
                );
            }
            seenElementUuids.add(uuid);
            assertEqual(
                cubeParentNames.get(uuid),
                entry.name,
                `${modelCube.name} Blockbench parent`
            );

            const bounds = blockbenchCubeBounds(modelCube);
            assertEqual(
                element.from,
                bounds.from,
                `${modelCube.name} Blockbench from`
            );
            assertEqual(
                element.to,
                bounds.to,
                `${modelCube.name} Blockbench to`
            );
            assertEqual(
                element.origin,
                modelCube.pivot
                    ? blockbenchVector(modelCube.pivot)
                    : [0, 0, 0],
                `${modelCube.name} Blockbench pivot`
            );
            assertEqual(
                element.rotation ?? [0, 0, 0],
                modelCube.rotation
                    ? blockbenchRotation(modelCube.rotation)
                    : [0, 0, 0],
                `${modelCube.name} Blockbench rotation`
            );
            assertEqual(
                element.uv_offset,
                modelCube.uv,
                `${modelCube.name} Blockbench UV`
            );
            assertEqual(
                element.faces,
                makeBoxUvFaces(modelCube),
                `${modelCube.name} Blockbench faces`
            );

            const size = element.to.map(
                (value, axis) => value - element.from[axis]
            );
            const roundTrippedCube = {
                origin: [
                    -(element.from[0] + size[0]),
                    element.from[1],
                    element.from[2]
                ],
                size,
                uv: element.uv_offset
            };
            if (element.rotation) {
                roundTrippedCube.pivot = blockbenchVector(element.origin);
                roundTrippedCube.rotation =
                    blockbenchRotation(element.rotation);
            }
            if (element.inflate !== undefined) {
                roundTrippedCube.inflate = element.inflate;
            }
            if (element.mirror_uv !== undefined) {
                roundTrippedCube.mirror = element.mirror_uv;
            }
            assertEqual(
                roundTrippedCube,
                cubeForJson(modelCube),
                `${modelCube.name} GeckoLib round trip`
            );
        });
    }

    if (seenElementUuids.size !== bbmodel.elements.length) {
        throw new Error("Blockbench outliner does not contain every cube");
    }

    if (bbmodel.textures.length !== 1) {
        throw new Error(
            `Blockbench texture count mismatch: ${bbmodel.textures.length}`
        );
    }
    const texture = bbmodel.textures[0];
    const prefix = "data:image/png;base64,";
    if (!texture.source.startsWith(prefix)) {
        throw new Error("Blockbench texture is not embedded as a PNG");
    }
    const embeddedTexture = Buffer.from(
        texture.source.slice(prefix.length),
        "base64"
    );
    if (!embeddedTexture.equals(textureBuffer)) {
        throw new Error(
            "Embedded Blockbench texture differs from young_deep_one.png"
        );
    }
    const expectedRelativePath = path
        .relative(path.dirname(bbmodelPath), texturePath)
        .split(path.sep)
        .join("/");
    assertEqual(
        texture.relative_path,
        expectedRelativePath,
        "Blockbench texture path"
    );
}

validateModel();
const bounds = calculateBounds();
const staticPoseValidation = validateStaticPose(bounds);
const textureBuffer = makeTexture();
const bbmodel = makeBbmodel(textureBuffer);
validateBbmodel(bbmodel, textureBuffer);

fs.mkdirSync(path.dirname(geometryPath), {recursive: true});
fs.mkdirSync(path.dirname(texturePath), {recursive: true});
fs.mkdirSync(path.dirname(uvGuidePath), {recursive: true});
fs.mkdirSync(path.dirname(bbmodelPath), {recursive: true});
fs.writeFileSync(geometryPath, `${JSON.stringify(geometry, null, 2)}\n`);
fs.writeFileSync(texturePath, textureBuffer);
fs.writeFileSync(uvGuidePath, makeUvGuide());
fs.writeFileSync(previewPath, makePreview());
fs.writeFileSync(
    previewPaths.front,
    makePreview([previewViews.front], {width: 768, height: 768})
);
fs.writeFileSync(
    previewPaths.right,
    makePreview([previewViews.right], {width: 768, height: 768})
);
fs.writeFileSync(
    previewPaths.back,
    makePreview([previewViews.back], {width: 768, height: 768})
);
fs.writeFileSync(
    previewPaths.leftFront,
    makePreview([previewViews.leftFront], {width: 768, height: 768})
);
fs.writeFileSync(
    previewPaths.lowAngle,
    makePreview([previewViews.lowAngle], {width: 768, height: 768})
);
fs.writeFileSync(bbmodelPath, `${JSON.stringify(bbmodel, null, 2)}\n`);
validateBbmodel(
    JSON.parse(fs.readFileSync(bbmodelPath, "utf8")),
    textureBuffer
);

console.log(`Generated ${path.relative(workspace, geometryPath)}`);
console.log(`Generated ${path.relative(workspace, texturePath)}`);
console.log(`Generated ${path.relative(workspace, uvGuidePath)}`);
console.log(`Generated ${path.relative(workspace, previewPath)}`);
for (const generatedPreviewPath of Object.values(previewPaths)) {
    console.log(
        `Generated ${path.relative(workspace, generatedPreviewPath)}`
    );
}
console.log(`Generated ${path.relative(workspace, bbmodelPath)}`);
console.log(`Bones: ${bones.length}`);
console.log(`Cubes: ${allCubes.length}`);
console.log(
    "Below ground: "
    + cubesBelowGround()
        .map((entry) =>
            `${entry.cubeName}=${entry.minimumY.toFixed(2)}`
        )
        .join(",")
);
console.log(
    "Above height 24: "
    + cubesAboveHeight(24 + 1e-6)
        .map((entry) =>
            `${entry.cubeName}=${entry.maximumY.toFixed(2)}`
        )
        .join(",")
);
console.log(
    `Torso clearance: ${staticPoseValidation.torsoClearance.toFixed(2)}; `
    + `hand spread ratio: `
    + staticPoseValidation.handSpreadRatio.toFixed(2)
);
console.log(
    "Support clearances: "
    + [
        staticPoseValidation.leftPalmClearance,
        staticPoseValidation.rightPalmClearance,
        staticPoseValidation.leftSoleClearance,
        staticPoseValidation.rightSoleClearance
    ].map((value) => value.toFixed(2)).join(",")
);
console.log(
    `Bounds: min=${bounds.minimum.map((value) => value.toFixed(2)).join(",")} `
    + `max=${bounds.maximum.map((value) => value.toFixed(2)).join(",")} `
    + `size=${bounds.size.map((value) => value.toFixed(2)).join(",")}`
);

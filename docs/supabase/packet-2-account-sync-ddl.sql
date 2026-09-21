-- Temper Account sync — Packet 2 (custom exercises + bodyweight)
-- Apply in the Supabase SQL editor for Allen's project before shipping the app build.
-- Built-in catalog seed rows are never uploaded; only owner-authored customs replicate.

-- Custom lifts (Library → custom exercises only)
CREATE TABLE IF NOT EXISTS public.custom_exercises (
    id TEXT PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    muscle_group TEXT NOT NULL,
    notes TEXT NOT NULL DEFAULT '',
    equipment TEXT NOT NULL,
    load_type TEXT NOT NULL,
    movement_key TEXT,
    image_key TEXT,
    name_key TEXT NOT NULL,
    created_at_ms BIGINT NOT NULL,
    updated_at_ms BIGINT NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    deleted_at_ms BIGINT
);

CREATE INDEX IF NOT EXISTS custom_exercises_user_updated_idx
    ON public.custom_exercises (user_id, updated_at_ms);

ALTER TABLE public.custom_exercises ENABLE ROW LEVEL SECURITY;

CREATE POLICY custom_exercises_select ON public.custom_exercises
    FOR SELECT TO authenticated
    USING (user_id = auth.uid());

CREATE POLICY custom_exercises_insert ON public.custom_exercises
    FOR INSERT TO authenticated
    WITH CHECK (user_id = auth.uid());

CREATE POLICY custom_exercises_update ON public.custom_exercises
    FOR UPDATE TO authenticated
    USING (user_id = auth.uid())
    WITH CHECK (user_id = auth.uid());

CREATE POLICY custom_exercises_delete ON public.custom_exercises
    FOR DELETE TO authenticated
    USING (user_id = auth.uid());

-- Muscle credits for custom lifts (child rows; parent must exist locally before pull applies)
CREATE TABLE IF NOT EXISTS public.custom_exercise_muscles (
    exercise_id TEXT NOT NULL,
    user_id UUID NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
    muscle_key TEXT NOT NULL,
    weight DOUBLE PRECISION NOT NULL,
    updated_at_ms BIGINT NOT NULL,
    deleted_at_ms BIGINT,
    PRIMARY KEY (user_id, exercise_id, muscle_key)
);

CREATE INDEX IF NOT EXISTS custom_exercise_muscles_user_updated_idx
    ON public.custom_exercise_muscles (user_id, updated_at_ms);

ALTER TABLE public.custom_exercise_muscles ENABLE ROW LEVEL SECURITY;

CREATE POLICY custom_exercise_muscles_select ON public.custom_exercise_muscles
    FOR SELECT TO authenticated
    USING (user_id = auth.uid());

CREATE POLICY custom_exercise_muscles_insert ON public.custom_exercise_muscles
    FOR INSERT TO authenticated
    WITH CHECK (user_id = auth.uid());

CREATE POLICY custom_exercise_muscles_update ON public.custom_exercise_muscles
    FOR UPDATE TO authenticated
    USING (user_id = auth.uid())
    WITH CHECK (user_id = auth.uid());

CREATE POLICY custom_exercise_muscles_delete ON public.custom_exercise_muscles
    FOR DELETE TO authenticated
    USING (user_id = auth.uid());

-- Weigh-in log (Room bodyweight_entries cache)
CREATE TABLE IF NOT EXISTS public.bodyweight_entries (
    epoch_day BIGINT NOT NULL,
    user_id UUID NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
    kg DOUBLE PRECISION NOT NULL,
    recorded_at_ms BIGINT NOT NULL,
    zone_id TEXT NOT NULL DEFAULT 'UTC',
    offset_seconds INTEGER NOT NULL DEFAULT 0,
    updated_at_ms BIGINT NOT NULL,
    deleted_at_ms BIGINT,
    PRIMARY KEY (user_id, epoch_day)
);

CREATE INDEX IF NOT EXISTS bodyweight_entries_user_updated_idx
    ON public.bodyweight_entries (user_id, updated_at_ms);

ALTER TABLE public.bodyweight_entries ENABLE ROW LEVEL SECURITY;

CREATE POLICY bodyweight_entries_select ON public.bodyweight_entries
    FOR SELECT TO authenticated
    USING (user_id = auth.uid());

CREATE POLICY bodyweight_entries_insert ON public.bodyweight_entries
    FOR INSERT TO authenticated
    WITH CHECK (user_id = auth.uid());

CREATE POLICY bodyweight_entries_update ON public.bodyweight_entries
    FOR UPDATE TO authenticated
    USING (user_id = auth.uid())
    WITH CHECK (user_id = auth.uid());

CREATE POLICY bodyweight_entries_delete ON public.bodyweight_entries
    FOR DELETE TO authenticated
    USING (user_id = auth.uid());

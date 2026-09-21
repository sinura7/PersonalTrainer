-- Temper Account sync — Packet 3 (goals, coach prefs, reminders, display, account profile)
-- Apply in the Supabase SQL editor for Allen's project before testing this build on a second device.
-- Built-in catalog seed, schedule/plan rows, and live ACTIVE sessions remain out of scope here.

-- Measurable goals (Room measurable_goals; pause is the paused flag on each row)
CREATE TABLE IF NOT EXISTS public.measurable_goals (
    id TEXT PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
    kind TEXT NOT NULL,
    target_value DOUBLE PRECISION NOT NULL,
    exercise_id TEXT,
    exercise_name TEXT,
    period TEXT NOT NULL,
    instant_ms BIGINT NOT NULL,
    zone_id TEXT NOT NULL,
    offset_seconds INTEGER NOT NULL,
    local_epoch_day BIGINT NOT NULL,
    paused BOOLEAN NOT NULL DEFAULT false,
    created_at_ms BIGINT NOT NULL,
    updated_at_ms BIGINT NOT NULL,
    deleted_at_ms BIGINT
);

CREATE INDEX IF NOT EXISTS measurable_goals_user_updated_idx
    ON public.measurable_goals (user_id, updated_at_ms);

ALTER TABLE public.measurable_goals ENABLE ROW LEVEL SECURITY;

CREATE POLICY measurable_goals_select ON public.measurable_goals
    FOR SELECT TO authenticated
    USING (user_id = auth.uid());

CREATE POLICY measurable_goals_insert ON public.measurable_goals
    FOR INSERT TO authenticated
    WITH CHECK (user_id = auth.uid());

CREATE POLICY measurable_goals_update ON public.measurable_goals
    FOR UPDATE TO authenticated
    USING (user_id = auth.uid())
    WITH CHECK (user_id = auth.uid());

CREATE POLICY measurable_goals_delete ON public.measurable_goals
    FOR DELETE TO authenticated
    USING (user_id = auth.uid());

-- Coach surfaces (goal, emphasis, equipment, age, place, focus, heat window)
CREATE TABLE IF NOT EXISTS public.coach_prefs (
    user_id UUID PRIMARY KEY REFERENCES auth.users (id) ON DELETE CASCADE,
    training_goal TEXT NOT NULL,
    training_emphasis TEXT NOT NULL,
    available_equipment TEXT[] NOT NULL DEFAULT '{}',
    training_age TEXT NOT NULL,
    training_place TEXT NOT NULL DEFAULT '',
    training_focus TEXT NOT NULL,
    heat_window TEXT NOT NULL,
    updated_at_ms BIGINT NOT NULL,
    deleted_at_ms BIGINT
);

CREATE INDEX IF NOT EXISTS coach_prefs_user_updated_idx
    ON public.coach_prefs (user_id, updated_at_ms);

ALTER TABLE public.coach_prefs ENABLE ROW LEVEL SECURITY;

CREATE POLICY coach_prefs_select ON public.coach_prefs
    FOR SELECT TO authenticated
    USING (user_id = auth.uid());

CREATE POLICY coach_prefs_insert ON public.coach_prefs
    FOR INSERT TO authenticated
    WITH CHECK (user_id = auth.uid());

CREATE POLICY coach_prefs_update ON public.coach_prefs
    FOR UPDATE TO authenticated
    USING (user_id = auth.uid())
    WITH CHECK (user_id = auth.uid());

CREATE POLICY coach_prefs_delete ON public.coach_prefs
    FOR DELETE TO authenticated
    USING (user_id = auth.uid());

-- Workout reminder settings (opt-out, quiet hours, per-weekday alarms)
CREATE TABLE IF NOT EXISTS public.reminder_prefs (
    user_id UUID PRIMARY KEY REFERENCES auth.users (id) ON DELETE CASCADE,
    reminder_opt_out BOOLEAN NOT NULL DEFAULT false,
    reminder_quiet_start_hour INTEGER NOT NULL DEFAULT 22,
    reminder_quiet_end_hour INTEGER NOT NULL DEFAULT 7,
    day_alarms TEXT[] NOT NULL DEFAULT '{}',
    updated_at_ms BIGINT NOT NULL,
    deleted_at_ms BIGINT
);

CREATE INDEX IF NOT EXISTS reminder_prefs_user_updated_idx
    ON public.reminder_prefs (user_id, updated_at_ms);

ALTER TABLE public.reminder_prefs ENABLE ROW LEVEL SECURITY;

CREATE POLICY reminder_prefs_select ON public.reminder_prefs
    FOR SELECT TO authenticated
    USING (user_id = auth.uid());

CREATE POLICY reminder_prefs_insert ON public.reminder_prefs
    FOR INSERT TO authenticated
    WITH CHECK (user_id = auth.uid());

CREATE POLICY reminder_prefs_update ON public.reminder_prefs
    FOR UPDATE TO authenticated
    USING (user_id = auth.uid())
    WITH CHECK (user_id = auth.uid());

CREATE POLICY reminder_prefs_delete ON public.reminder_prefs
    FOR DELETE TO authenticated
    USING (user_id = auth.uid());

-- Display choices (units, clock, bodyweight check-in weekday)
CREATE TABLE IF NOT EXISTS public.display_prefs (
    user_id UUID PRIMARY KEY REFERENCES auth.users (id) ON DELETE CASCADE,
    weight_unit TEXT NOT NULL,
    clock_format TEXT NOT NULL,
    bodyweight_check_in_weekday TEXT,
    updated_at_ms BIGINT NOT NULL,
    deleted_at_ms BIGINT
);

CREATE INDEX IF NOT EXISTS display_prefs_user_updated_idx
    ON public.display_prefs (user_id, updated_at_ms);

ALTER TABLE public.display_prefs ENABLE ROW LEVEL SECURITY;

CREATE POLICY display_prefs_select ON public.display_prefs
    FOR SELECT TO authenticated
    USING (user_id = auth.uid());

CREATE POLICY display_prefs_insert ON public.display_prefs
    FOR INSERT TO authenticated
    WITH CHECK (user_id = auth.uid());

CREATE POLICY display_prefs_update ON public.display_prefs
    FOR UPDATE TO authenticated
    USING (user_id = auth.uid())
    WITH CHECK (user_id = auth.uid());

CREATE POLICY display_prefs_delete ON public.display_prefs
    FOR DELETE TO authenticated
    USING (user_id = auth.uid());

-- Thin account profile (save posture; email stays in Supabase Auth)
CREATE TABLE IF NOT EXISTS public.account_profiles (
    user_id UUID PRIMARY KEY REFERENCES auth.users (id) ON DELETE CASCADE,
    save_posture TEXT NOT NULL,
    save_posture_chosen BOOLEAN NOT NULL DEFAULT false,
    updated_at_ms BIGINT NOT NULL,
    deleted_at_ms BIGINT
);

CREATE INDEX IF NOT EXISTS account_profiles_user_updated_idx
    ON public.account_profiles (user_id, updated_at_ms);

ALTER TABLE public.account_profiles ENABLE ROW LEVEL SECURITY;

CREATE POLICY account_profiles_select ON public.account_profiles
    FOR SELECT TO authenticated
    USING (user_id = auth.uid());

CREATE POLICY account_profiles_insert ON public.account_profiles
    FOR INSERT TO authenticated
    WITH CHECK (user_id = auth.uid());

CREATE POLICY account_profiles_update ON public.account_profiles
    FOR UPDATE TO authenticated
    USING (user_id = auth.uid())
    WITH CHECK (user_id = auth.uid());

CREATE POLICY account_profiles_delete ON public.account_profiles
    FOR DELETE TO authenticated
    USING (user_id = auth.uid());

CREATE TABLE IF NOT EXISTS public.plan (
  id         serial PRIMARY KEY,
  plan_id    serial NOT NULL,
  plan_name  VARCHAR(50),
  account_id serial
);

INSERT INTO public.plan (id, plan_id, plan_name, account_id) VALUES (
  1, 1, '测试计划', 1
)
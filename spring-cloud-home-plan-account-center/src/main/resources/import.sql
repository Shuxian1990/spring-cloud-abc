CREATE TABLE IF NOT EXISTS public.account (
  id           serial PRIMARY KEY,
  account_name VARCHAR(50)  NOT NULL,
  login_name   VARCHAR(50),
  pwd          VARCHAR(128) NOT NULL
);

INSERT INTO public.account (account_name, login_name, pwd)
VALUES ('test', 'test', '123') ON CONFLICT DO NOTHING;
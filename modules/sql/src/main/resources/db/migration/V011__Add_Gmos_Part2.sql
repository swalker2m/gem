--
-- Name: e_gmos_adc; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE e_gmos_adc (
    id               identifier            PRIMARY KEY,
    short_name       character varying(11) NOT NULL,
    long_name        character varying(22) NOT NULL
);

ALTER TABLE e_gmos_adc OWNER TO postgres;

--
-- Data for Name: e_gmos_adc; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY e_gmos_adc (id, short_name, long_name) FROM stdin;
None	None	No Correction
BestStatic	Best Static	Best Static Correction
Follow	Follow	Follow During Exposure
\.


--
-- Name: e_gmos_amp_count; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE e_gmos_amp_count (
    id               identifier            PRIMARY KEY,
    short_name       character varying(10) NOT NULL,
    long_name        character varying(10) NOT NULL
);

ALTER TABLE e_gmos_amp_count OWNER TO postgres;

--
-- Data for Name: e_gmos_amp_count; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY e_gmos_amp_count (id, short_name, long_name) FROM stdin;
Three	Three	Three
Six	Six	Six
Twelve	Twelve	Twelve
\.


--
-- Name: e_gmos_amp_gain; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE e_gmos_amp_gain (
    id               identifier            PRIMARY KEY,
    short_name       character varying(10) NOT NULL,
    long_name        character varying(10) NOT NULL
);

ALTER TABLE e_gmos_amp_gain OWNER TO postgres;

--
-- Data for Name: e_gmos_amp_gain; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY e_gmos_amp_gain (id, short_name, long_name) FROM stdin;
Low	Low	Low
High	High	High
\.


--
-- Name: e_gmos_amp_read_mode; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE e_gmos_amp_read_mode (
    id               identifier           PRIMARY KEY,
    short_name       character varying(4) NOT NULL,
    long_name        character varying(4) NOT NULL
);

ALTER TABLE e_gmos_amp_read_mode OWNER TO postgres;

--
-- Data for Name: e_gmos_amp_read_mode; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY e_gmos_amp_read_mode (id, short_name, long_name) FROM stdin;
Slow	slow	Slow
Fast	fast	Fast
\.


--
-- Name: e_gmos_binning; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE e_gmos_binning (
    id               identifier           PRIMARY KEY,
    short_name       character varying(4) NOT NULL,
    long_name        character varying(4) NOT NULL,
    count            smallint             NOT NULL
);

ALTER TABLE e_gmos_binning OWNER TO postgres;

--
-- Data for Name: e_gmos_binning ; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY e_gmos_binning (id, short_name, long_name, count) FROM stdin;
One	1	One	1
Two	2	Two	2
Four	4	Four	4
\.


--
-- Name: e_gmos_detector_order; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE e_gmos_detector_order (
    id               identifier            PRIMARY KEY,
    short_name       character varying(10) NOT NULL,
    long_name        character varying(10) NOT NULL,
    count            smallint              NOT NULL
);

ALTER TABLE e_gmos_detector_order OWNER TO postgres;


--
-- Data for Name: e_gmos_detector_order; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY e_gmos_detector_order (id, short_name, long_name, count) FROM stdin;
Zero	0	Zero	0
One	1	One	1
Two	2	Two	2
\.


--
-- Name: e_gmos_north_stage_mode; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE e_gmos_north_stage_mode (
    id         identifier            PRIMARY KEY,
    short_name character varying(10) NOT NULL,
    long_name  character varying(20) NOT NULL,
    obsolete   boolean               NOT NULL
);

ALTER TABLE e_gmos_north_stage_mode OWNER TO postgres;


--
-- Data for Name: e_gmos_north_stage_mode; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY e_gmos_north_stage_mode (id, short_name, long_name, obsolete) FROM stdin;
NoFollow	No Follow	Do Not Follow	f
FollowXyz	Follow XYZ	Follow in XYZ(focus)	t
FollowXy	Follow XY	Follow in XY	f
FollowZ	Follow Z	Follow in Z Only	t
\.



--
-- Name: e_gmos_south_stage_mode; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE e_gmos_south_stage_mode (
    id         identifier            PRIMARY KEY,
    short_name character varying(12) NOT NULL,
    long_name  character varying(30) NOT NULL,
    obsolete   boolean               NOT NULL
);

ALTER TABLE e_gmos_south_stage_mode OWNER TO postgres;


--
-- Data for Name: e_gmos_south_stage_mode; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY e_gmos_south_stage_mode (id, short_name, long_name, obsolete) FROM stdin;
NoFollow	No Follow	Do Not Follow	f
FollowXyz	Follow XYZ	Follow in XYZ(focus)	f
FollowXy	Follow XY	Follow in XY	t
FollowZ	Follow Z	Follow in Z Only	f
\.

